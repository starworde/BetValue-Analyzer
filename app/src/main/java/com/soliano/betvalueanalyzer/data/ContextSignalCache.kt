package com.soliano.betvalueanalyzer.data

import com.soliano.betvalueanalyzer.domain.MatchContextSignal
import java.text.Normalizer

data class PersistedContextSignal(
    val matchKey: String,
    val commenceTime: Long,
    val teamName: String,
    val title: String,
    val publishers: List<String>,
    val impact: Double,
    val category: String,
    val publishedAt: Long,
    val lastConfirmedAt: Long,
) {
    fun toDomain(): MatchContextSignal = MatchContextSignal(
        teamName = teamName,
        title = title,
        publishers = publishers,
        impact = impact,
        category = category,
        publishedAt = publishedAt,
    )
}

interface ContextSignalStore {
    suspend fun loadContextSignals(): List<PersistedContextSignal>
    suspend fun saveContextSignals(signals: List<PersistedContextSignal>)
}

object NoOpContextSignalStore : ContextSignalStore {
    override suspend fun loadContextSignals(): List<PersistedContextSignal> = emptyList()
    override suspend fun saveContextSignals(signals: List<PersistedContextSignal>) = Unit
}

data class ContextSignalMergeResult(
    val reports: Map<String, NewsContextReport>,
    val persisted: List<PersistedContextSignal>,
)

object ContextSignalCache {
    fun merge(
        requests: List<NewsContextRequest>,
        fresh: Map<String, NewsContextReport>,
        stored: List<PersistedContextSignal>,
        now: Long,
    ): ContextSignalMergeResult {
        val requestsByKey = requests.associateBy { it.key }
        val active = stored.filter { cached ->
            now <= cached.commenceTime + POST_MATCH_RETENTION_MS
        }.toMutableList()

        fresh.forEach { (matchKey, report) ->
            val request = requestsByKey[matchKey] ?: return@forEach
            report.signals.sortedBy { it.publishedAt }.forEach { signal ->
                if (signal.impact > 0) {
                    active.removeAll { cached ->
                        cached.matchKey == matchKey &&
                            cached.teamName.normalized() == signal.teamName.normalized() &&
                            cached.impact < 0 &&
                            cached.publishedAt < signal.publishedAt &&
                            sameSubject(cached.title, signal.title, signal.teamName)
                    }
                }
                val persisted = PersistedContextSignal(
                    matchKey = matchKey,
                    commenceTime = request.commenceTime,
                    teamName = signal.teamName,
                    title = signal.title,
                    publishers = signal.publishers,
                    impact = signal.impact,
                    category = signal.category,
                    publishedAt = signal.publishedAt,
                    lastConfirmedAt = now,
                )
                val identity = persisted.identity()
                active.removeAll { it.identity() == identity }
                active += persisted
            }
        }

        val compact = active
            .sortedByDescending { it.lastConfirmedAt }
            .distinctBy { it.identity() }
            .take(MAX_PERSISTED_SIGNALS)
        val mergedReports = requests.associate { request ->
            val current = fresh[request.key] ?: NewsContextReport()
            val retained = compact.filter { it.matchKey == request.key }.map { it.toDomain() }
            request.key to current.copy(
                signals = retained.sortedBy { it.impact },
                checkedSources = (
                    current.checkedSources +
                        retained.flatMap { it.publishers } +
                        listOf("Google Actualités", "Bing Actualités")
                    ).filter { it.isNotBlank() }.distinct(),
            )
        }
        return ContextSignalMergeResult(mergedReports, compact)
    }

    private fun PersistedContextSignal.identity(): String = listOf(
        matchKey,
        teamName.normalized(),
        category.normalized(),
        subjectKey(title, teamName),
    ).joinToString("|")

    private fun sameSubject(first: String, second: String, teamName: String): Boolean {
        val firstNames = subjectTokens(first, teamName)
        val secondNames = subjectTokens(second, teamName)
        return firstNames.isNotEmpty() && secondNames.isNotEmpty() && firstNames.any(secondNames::contains)
    }

    private fun subjectKey(title: String, teamName: String): String =
        subjectTokens(title, teamName).sorted().take(3).joinToString("-")
            .ifBlank { title.normalized().take(48) }

    private fun subjectTokens(title: String, teamName: String): Set<String> {
        val teamTokens = WORD.findAll(teamName).map { it.value.normalized() }.toSet()
        return PROPER_NAME.findAll(title)
            .map { it.value.normalized() }
            .filter { it.length >= 4 && it !in teamTokens && it !in GENERIC_TOKENS }
            .toSet()
    }

    private fun String.normalized(): String = Normalizer.normalize(lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), "")

    private const val POST_MATCH_RETENTION_MS = 2L * 60 * 60 * 1000
    private const val MAX_PERSISTED_SIGNALS = 300
    private val WORD = Regex("[\\p{L}]{2,}")
    private val PROPER_NAME = Regex("(?<![\\p{L}])[A-ZÀ-ÖØ-Þ][\\p{L}'’-]{2,}")
    private val GENERIC_TOKENS = setOf(
        "football", "mondial", "coupe", "equipe", "match", "retour", "absent", "absence",
        "blessure", "blesse", "forfait", "suspendu", "selection", "officiel", "derniere",
    )
}
