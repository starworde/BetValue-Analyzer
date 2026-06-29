package com.soliano.betvalueanalyzer.data

import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import com.soliano.betvalueanalyzer.domain.MatchContextSignal
import java.io.StringReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.w3c.dom.Element
import org.xml.sax.InputSource

data class NewsContextRequest(
    val key: String,
    val homeTeam: String,
    val awayTeam: String,
    val extraQueries: List<String> = emptyList(),
    val commenceTime: Long = Long.MAX_VALUE,
    val subjects: List<NewsSubject> = emptyList(),
    val lookbackDays: Int = 30,
)

data class NewsSubject(
    val query: String,
    val teamName: String? = null,
    val label: String,
    val type: String,
)

data class NewsContextReport(
    val signals: List<MatchContextSignal> = emptyList(),
    val checkedSources: List<String> = emptyList(),
)

class NewsContextProvider(private val api: PublicSportsApiService) {
    private val cache = mutableMapOf<String, CachedSignals>()
    private val semaphore = Semaphore(4)
    private val feedSemaphore = Semaphore(4)

    suspend fun load(requests: List<NewsContextRequest>): Map<String, NewsContextReport> = coroutineScope {
        requests.distinctBy { it.key }.take(10).map { request ->
            async {
                semaphore.withPermit {
                    val cacheKey = request.cacheKey()
                    val cached = cache[cacheKey]?.takeIf { System.currentTimeMillis() - it.updatedAt < CACHE_MS }
                    val report = cached?.report ?: fetch(request).also {
                        cache[cacheKey] = CachedSignals(it, System.currentTimeMillis())
                    }
                    request.key to report
                }
            }
        }.awaitAll().toMap()
    }

    private suspend fun fetch(request: NewsContextRequest): NewsContextReport {
        val querySubjects = buildList {
            add(NewsSubject("${request.homeTeam} ${request.awayTeam} ${defaultSportTerm(request)}", null, "Match", "MATCH"))
            request.extraQueries.forEach { add(NewsSubject(it, null, it, "EXTRA")) }
            addAll(request.subjects)
        }.distinctBy { it.query }
        val feeds = querySubjects.flatMap { subject ->
            val query = URLEncoder.encode(subject.query, StandardCharsets.UTF_8.toString())
            buildList {
                add(Feed("Google Actualités", "https://news.google.com/rss/search?q=$query&hl=fr&gl=FR&ceid=FR:fr", subject))
                add(Feed("Bing Actualités", "https://www.bing.com/news/search?q=$query&format=rss", subject))
                if (subject.isSocialSearch()) {
                    add(Feed("Brave Search public", "https://search.brave.com/search?q=$query&source=web", subject, webSearch = true))
                }
            }
        }
        val items = coroutineScope {
            feeds.map { feed ->
                async {
                    feedSemaphore.withPermit {
                        getRaw(feed.url)?.let {
                            if (feed.webSearch) parsePublicSocialResults(it, feed.aggregator, feed.subject)
                            else parseFeed(it, feed.aggregator, feed.subject)
                        }.orEmpty()
                    }
                }
            }.awaitAll().flatten()
        }.filter { item ->
            val age = System.currentTimeMillis() - item.publishedAt
            age in 0..request.lookbackDays.coerceIn(1, 90) * DAY_MS
        }
        val classified = items.filter { it.eligibleForSignal }.mapNotNull { classify(it, request) }
        val groupedSignals = classified.groupBy { "${normalize(it.teamName)}:${it.category}" }.values
        val confirmed = groupedSignals.mapNotNull { group ->
            val publishers = group.flatMap { it.publishers }.distinct()
            if (publishers.size < 2) return@mapNotNull null
            val representative = group.maxByOrNull { it.publishedAt } ?: return@mapNotNull null
            representative.copy(
                publishers = publishers.take(5),
                impact = group.map { it.impact }.average().coerceIn(-0.05, 0.03),
            )
        }
        val recentSingleSource = groupedSignals.mapNotNull { group ->
            val publishers = group.flatMap { it.publishers }.distinct()
            if (publishers.size >= 2) return@mapNotNull null
            val representative = group.maxByOrNull { it.publishedAt } ?: return@mapNotNull null
            representative.copy(
                publishers = publishers.ifEmpty { listOf(representative.publishers.firstOrNull() ?: representative.category) }.take(2),
                category = "À confirmer · ${representative.category}",
                impact = (representative.impact * 0.45).coerceIn(-0.022, 0.014),
            )
        }
            .sortedByDescending { it.publishedAt }
            .take(MAX_SINGLE_SOURCE_SIGNALS)
        val returns = confirmed.filter { it.category == "Retour important" }
        val activeSignals = (confirmed + recentSingleSource).filterNot { signal ->
            signal.impact < 0 && returns.any { returned ->
                normalize(returned.teamName) == normalize(signal.teamName) &&
                    returned.publishedAt > signal.publishedAt &&
                    sameSubject(signal.title, returned.title, signal.teamName)
            }
        }.sortedBy { it.impact }
        val socialChannels = feeds.filter { it.subject.isSocialSearch() }
            .flatMap { listOf("instagram.com", "facebook.com", "x.com") }
        val checkedSources = (feeds.map { it.aggregator } + socialChannels + items.flatMap { it.publishers })
            .filter { it.isNotBlank() }.distinct().take(MAX_CHECKED_SOURCES)
        return NewsContextReport(activeSignals, checkedSources)
    }

    private fun defaultSportTerm(request: NewsContextRequest): String {
        val subjectText = request.subjects.joinToString(" ") { "${it.type} ${it.query}" }.lowercase()
        return when {
            "cycling" in subjectText || "cyclisme" in subjectText -> "cyclisme"
            "formula 1" in subjectText || "f1" in subjectText -> "Formula 1"
            "rugby" in subjectText -> "rugby"
            else -> "football"
        }
    }

    private fun sameSubject(first: String, second: String, teamName: String): Boolean {
        val excluded = WORD.findAll(teamName).map { normalize(it.value) }.toSet() + GENERIC_TOKENS
        fun names(value: String): Set<String> = PROPER_NAME.findAll(value)
            .map { normalize(it.value) }.filter { it.length >= 4 && it !in excluded }.toSet()
        val firstNames = names(first)
        val secondNames = names(second)
        return firstNames.isNotEmpty() && firstNames.any(secondNames::contains)
    }

    private fun classify(item: NewsItem, request: NewsContextRequest): MatchContextSignal? {
        val normalizedTitle = normalize(item.title)
        val team = when {
            normalizedTitle.contains(normalize(request.homeTeam)) -> request.homeTeam
            normalizedTitle.contains(normalize(request.awayTeam)) -> request.awayTeam
            item.subject.teamName != null && normalizedTitle.contains(normalize(item.subject.label)) -> item.subject.teamName
            else -> return null
        }
        val (category, impact) = when {
            isConfirmedReturn(normalizedTitle) -> "Retour important" to 0.02
            NEGATIVE_COACH.any { normalizedTitle.contains(normalize(it)) } &&
                (COACH_WORDS.any { normalizedTitle.contains(normalize(it)) } || item.subject.type.contains("COACH")) -> "Encadrement" to -0.035
            NEGATIVE_PLAYER.any { normalizedTitle.contains(normalize(it)) } -> "Absence ou blessure" to -0.04
            NEGATIVE_MECHANICAL.any { normalizedTitle.contains(normalize(it)) } -> "Fiabilité, incident ou pénalité" to -0.025
            POSITIVE_TECHNICAL.any { normalizedTitle.contains(normalize(it)) } -> "Amélioration ou performance technique" to 0.018
            NEGATIVE_MOMENTUM.any { normalizedTitle.contains(normalize(it)) } &&
                NEGATED_NEGATIVE.none { normalizedTitle.contains(normalize(it)) } -> "Fatigue, pression ou méforme" to -0.018
            POSITIVE_MOMENTUM.any { normalizedTitle.contains(normalize(it)) } -> "Confiance ou dynamique positive" to 0.015
            else -> return null
        }
        return MatchContextSignal(
            teamName = team,
            title = item.title,
            publishers = item.publishers.ifEmpty { listOf(item.aggregator) },
            impact = impact,
            category = category,
            publishedAt = item.publishedAt,
        )
    }

    private fun isConfirmedReturn(normalizedTitle: String): Boolean {
        if (RETURN_FALSE_POSITIVES.any { normalizedTitle.contains(normalize(it)) }) return false
        if (POSITIVE_AVAILABILITY.any { normalizedTitle.contains(normalize(it)) }) return true
        val hasReturn = listOf("de retour", "retour de", "revient", "reprend").any { normalizedTitle.contains(normalize(it)) }
        return hasReturn && RETURN_TO_PLAY.any { normalizedTitle.contains(normalize(it)) }
    }

    private suspend fun getRaw(url: String): String? {
        val response = runCatching { api.getRawUrl(url) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        return response.body()?.string()
    }

    private fun parseFeed(xml: String, aggregator: String, subject: NewsSubject): List<NewsItem> = runCatching {
        if (xml.contains("<!DOCTYPE html", ignoreCase = true)) return@runCatching emptyList()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val nodes = document.getElementsByTagName("item")
        (0 until nodes.length).mapNotNull { index ->
            val item = nodes.item(index) as? Element ?: return@mapNotNull null
            val title = item.childText("title") ?: return@mapNotNull null
            val publisher = item.childText("source")
                ?: item.childText("News:Source")
                ?: title.substringAfterLast(" - ", "")
            val description = item.childText("description").orEmpty()
            val clusteredPublishers = Regex("<font[^>]*>([^<]+)</font>", RegexOption.IGNORE_CASE)
                .findAll(description).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toList()
            val published = item.childText("pubDate")?.let {
                runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            } ?: return@mapNotNull null
            NewsItem(title, (listOf(publisher) + clusteredPublishers).filter { it.isNotBlank() }.distinct(), aggregator, published, subject)
        }
    }.getOrDefault(emptyList())

    private fun parsePublicSocialResults(html: String, aggregator: String, subject: NewsSubject): List<NewsItem> {
        val socialUrl = Regex("https?://(?:www\\.)?(instagram\\.com|facebook\\.com|x\\.com)/[^\\\"&<> ]+", RegexOption.IGNORE_CASE)
        val parsed = socialUrl.findAll(html).map { match ->
            val start = (match.range.first - 350).coerceAtLeast(0)
            val end = (match.range.last + 650).coerceAtMost(html.lastIndex)
            val surrounding = html.substring(start, end + 1)
                .replace(Regex("<[^>]+>"), " ")
                .replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")
                .replace(Regex("\\s+"), " ").trim().take(500)
            NewsItem(
                title = surrounding.ifBlank { "Compte social public correspondant à ${subject.label}" },
                publishers = listOf(match.groupValues[1].lowercase()),
                aggregator = aggregator,
                publishedAt = System.currentTimeMillis(),
                subject = subject,
                eligibleForSignal = subject.isSocialSearch() && surrounding.looksActionableSocialSignal(),
            )
        }.distinctBy { it.publishers.firstOrNull() to it.title }.take(8).toList()
        if (parsed.isNotEmpty()) return parsed
        if (!subject.isSocialSearch()) return emptyList()
        return listOf("instagram.com", "facebook.com", "x.com").map { domain ->
            NewsItem(
                title = "Canal social public verifie pour ${subject.label} sur $domain",
                publishers = listOf(domain),
                aggregator = aggregator,
                publishedAt = System.currentTimeMillis(),
                subject = subject,
                eligibleForSignal = false,
            )
        }
    }

    private fun Element.childText(name: String): String? {
        val direct = getElementsByTagName(name)
        if (direct.length > 0) return direct.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
        val local = getElementsByTagNameNS("*", name.substringAfter(':'))
        return if (local.length > 0) local.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() } else null
    }

    private fun NewsSubject.isSocialSearch(): Boolean {
        val value = "$type $query".lowercase()
        return type.contains("SOCIAL", ignoreCase = true) ||
            listOf("instagram", "facebook", "x.com", "twitter", "réseau", "reseau", "social", "compte officiel").any { it in value }
    }

    private fun String.looksActionableSocialSignal(): Boolean {
        val normalized = normalize(this)
        val keywords = NEGATIVE_PLAYER + POSITIVE_AVAILABILITY + NEGATIVE_MOMENTUM +
            POSITIVE_MOMENTUM + NEGATIVE_MECHANICAL + POSITIVE_TECHNICAL +
            listOf("carton", "suspension", "suspendu", "retour", "forfait", "injury", "training", "lineup")
        return keywords.map(::normalize).any { it.isNotBlank() && normalized.contains(it) }
    }

    private fun normalize(value: String): String = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), "")

    private fun NewsContextRequest.cacheKey(): String = "$key:$lookbackDays:${subjects.joinToString("|") { it.query }.hashCode()}"

    private data class Feed(val aggregator: String, val url: String, val subject: NewsSubject, val webSearch: Boolean = false)
    private data class NewsItem(
        val title: String,
        val publishers: List<String>,
        val aggregator: String,
        val publishedAt: Long,
        val subject: NewsSubject,
        val eligibleForSignal: Boolean = true,
    )
    private data class CachedSignals(val report: NewsContextReport, val updatedAt: Long)

    private companion object {
        const val CACHE_MS = 5L * 60 * 1000
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val MAX_CHECKED_SOURCES = 30
        const val MAX_SINGLE_SOURCE_SIGNALS = 6
        val WORD = Regex("[\\p{L}]{2,}")
        val PROPER_NAME = Regex("(?<![\\p{L}])[A-ZÀ-ÖØ-Þ][\\p{L}'’-]{2,}")
        val GENERIC_TOKENS = setOf("football", "mondial", "coupe", "equipe", "match", "retour", "absent", "blessure", "forfait")
        val COACH_WORDS = listOf("coach", "entraineur", "selectionneur", "manager", "staff", "banc")
        val NEGATIVE_COACH = listOf("absent", "deuil", "deces", "mort", "manquera", "remplace", "rentre", "quitte", "interim", "intérim", "relais")
        val NEGATIVE_PLAYER = listOf("bless", "forfait", "indisponible", "suspend", "carton rouge", "manquera", "absent", "chute", "malade", "covid", "virus")
        val POSITIVE_AVAILABILITY = listOf("apte", "disponible", "autorise a jouer", "autorisé à jouer", "feu vert medical", "feu vert médical")
        val RETURN_TO_PLAY = listOf("avec le groupe", "a l entrainement", "à l'entraînement", "sur le terrain", "dans le groupe", "dans l effectif", "avec les bleus", "avec l equipe")
        val RETURN_FALSE_POSITIVES = listOf("retour en france", "retour au pays", "retour chez lui", "retour chez elle", "retour pour les obseques", "retour pour les obsèques")
        val NEGATED_NEGATIVE = listOf("zero doute", "zéro doute", "aucun doute", "sans aucun doute", "pas de doute")
        val NEGATIVE_MOMENTUM = listOf(
            "baisse de forme", "meforme", "méforme", "coup de mou", "fatigue", "epuise", "épuisé",
            "crise", "tension", "pression", "doute", "mauvaise serie", "mauvaise série", "demotive", "démotivé",
        )
        val POSITIVE_MOMENTUM = listOf(
            "en grande forme", "en forme", "pleine confiance", "serie de victoires", "série de victoires",
            "motive", "motivé", "record", "invaincu", "boost", "objectif", "doit gagner", "zero doute", "zéro doute",
        )
        val NEGATIVE_MECHANICAL = listOf(
            "penalite", "pénalité", "recul sur la grille", "moteur", "boite de vitesses", "boîte de vitesses",
            "fiabilite", "fiabilité", "panne", "accident", "crash", "probleme technique", "problème technique",
            "sanction", "abandon", "fond plat endommage", "fond plat endommagé",
        )
        val POSITIVE_TECHNICAL = listOf(
            "amelioration", "amélioration", "upgrade", "nouveau package", "evolution", "évolution",
            "pole position", "meilleur temps", "tres rapide", "très rapide", "gain de performance",
        )
    }
}
