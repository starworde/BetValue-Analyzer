package com.soliano.betvalueanalyzer.ui.components

import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.data.local.UpcomingEventEntity
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

fun displayTeamName(value: String): String {
    val trimmed = cleanDisplayText(value).trim()
    if (trimmed.isBlank()) return trimmed
    return cleanDisplayText(nationalTeamNames[canonicalName(trimmed)] ?: trimmed)
}

fun displayEventTitle(event: UpcomingEventEntity): String {
    if (event.participantA.isNotBlank() && event.participantB.isNotBlank()) {
        return cleanDisplayText("${displayTeamName(event.participantA)} — ${displayTeamName(event.participantB)}")
    }
    return translateNationalTeamsInText(cleanDisplayText(event.eventName))
}

fun displayPredictionTeams(prediction: PredictionEntity): String {
    if (prediction.sportKey.startsWith("racing")) return cleanDisplayText(prediction.competitionName)
    if (prediction.sportKey.startsWith("cycling")) return translateNationalTeamsInText(prediction.homeTeam)
    return "${displayTeamName(prediction.homeTeam)} — ${displayTeamName(prediction.awayTeam)}"
}

fun searchableText(event: UpcomingEventEntity): String = listOf(
    cleanDisplayText(event.sportTitle),
    cleanDisplayText(event.competitionName),
    cleanDisplayText(event.eventName),
    event.participantA,
    event.participantB,
    displayEventTitle(event),
    displayTeamName(event.participantA),
    displayTeamName(event.participantB),
).joinToString(" ")

fun searchableText(values: List<String>): String = values.joinToString(" ")

fun cleanDisplayText(value: String): String {
    if (value.length <= CLEAN_CACHE_MAX_TEXT_LENGTH) {
        cleanDisplayTextCache[value]?.let { return it }
    }
    var result = value
    repeat(3) {
        if (!looksMojibake(result)) return@repeat
        val fixed = runCatching {
            String(result.toByteArray(WINDOWS_1252), StandardCharsets.UTF_8)
        }.getOrNull()
        if (
            fixed != null &&
            fixed != result &&
            mojibakeScore(fixed) < mojibakeScore(result) &&
            '\uFFFD' !in fixed
        ) {
            result = fixed
        }
    }
    hardMojibakeReplacements.forEach { (bad, good) ->
        result = result.replace(bad, good)
    }
    if (value.length <= CLEAN_CACHE_MAX_TEXT_LENGTH) {
        if (cleanDisplayTextCache.size > CLEAN_CACHE_MAX_ENTRIES) cleanDisplayTextCache.clear()
        cleanDisplayTextCache[value] = result
    }
    return result
}

fun matchesSearch(haystack: String, query: String): Boolean {
    val needle = normalizeForSearch(query)
    if (needle.isBlank()) return true
    return normalizeForSearch(haystack).contains(needle)
}

fun normalizedSearchText(value: String): String = normalizeForSearch(value)

fun matchesNormalizedSearch(normalizedHaystack: String, normalizedNeedle: String): Boolean =
    normalizedNeedle.isBlank() || normalizedHaystack.contains(normalizedNeedle)

private fun translateNationalTeamsInText(value: String): String {
    var result = value.replace(Regex("\\s+vs\\.?\\s+", RegexOption.IGNORE_CASE), " — ")
    replacementNames.forEach { (source, translated) ->
        val pattern = Regex("(?<![\\p{L}])${Regex.escape(source)}(?![\\p{L}])", RegexOption.IGNORE_CASE)
        result = pattern.replace(result, cleanDisplayText(translated))
    }
    return cleanDisplayText(result)
}

private val WINDOWS_1252: Charset = Charset.forName("Windows-1252")
private const val CLEAN_CACHE_MAX_TEXT_LENGTH = 240
private const val CLEAN_CACHE_MAX_ENTRIES = 5_000
private val cleanDisplayTextCache = ConcurrentHashMap<String, String>()

private fun looksMojibake(value: String): Boolean = mojibakeScore(value) > 0

private fun mojibakeScore(value: String): Int = value.count { it in setOf('Ã', 'Â', 'â', '€', '™', '�') }

private val hardMojibakeReplacements = listOf(
    "â€™" to "’",
    "â€˜" to "‘",
    "â€œ" to "“",
    "â€�" to "”",
    "â€”" to "—",
    "â€“" to "–",
    "â€¦" to "…",
    "Â«" to "«",
    "Â»" to "»",
    "Â·" to "·",
    "Â " to " ",
)

private fun normalizeForSearch(value: String): String = Normalizer.normalize(value.lowercase(Locale.FRANCE), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun canonicalName(value: String): String = normalizeForSearch(value).replace(" ", "")

private val nationalTeamNames = mapOf(
    "afghanistan" to "Afghanistan",
    "albania" to "Albanie",
    "algeria" to "Algérie",
    "andorra" to "Andorre",
    "angola" to "Angola",
    "argentina" to "Argentine",
    "armenia" to "Arménie",
    "australia" to "Australie",
    "austria" to "Autriche",
    "azerbaijan" to "Azerbaïdjan",
    "belgium" to "Belgique",
    "bolivia" to "Bolivie",
    "bosniaherzegovina" to "Bosnie-Herzégovine",
    "brazil" to "Brésil",
    "bulgaria" to "Bulgarie",
    "cameroon" to "Cameroun",
    "canada" to "Canada",
    "chile" to "Chili",
    "china" to "Chine",
    "colombia" to "Colombie",
    "costarica" to "Costa Rica",
    "croatia" to "Croatie",
    "czechia" to "République tchèque",
    "czechrepublic" to "République tchèque",
    "denmark" to "Danemark",
    "ecuador" to "Équateur",
    "egypt" to "Égypte",
    "england" to "Angleterre",
    "finland" to "Finlande",
    "france" to "France",
    "germany" to "Allemagne",
    "ghana" to "Ghana",
    "greece" to "Grèce",
    "hungary" to "Hongrie",
    "iceland" to "Islande",
    "ireland" to "Irlande",
    "republicofireland" to "Irlande",
    "israel" to "Israël",
    "italy" to "Italie",
    "ivorycoast" to "Côte d’Ivoire",
    "cotedivoire" to "Côte d’Ivoire",
    "japan" to "Japon",
    "korearepublic" to "Corée du Sud",
    "southkorea" to "Corée du Sud",
    "mexico" to "Mexique",
    "morocco" to "Maroc",
    "netherlands" to "Pays-Bas",
    "newzealand" to "Nouvelle-Zélande",
    "nigeria" to "Nigéria",
    "northernireland" to "Irlande du Nord",
    "norway" to "Norvège",
    "paraguay" to "Paraguay",
    "peru" to "Pérou",
    "poland" to "Pologne",
    "portugal" to "Portugal",
    "romania" to "Roumanie",
    "scotland" to "Écosse",
    "senegal" to "Sénégal",
    "serbia" to "Serbie",
    "slovakia" to "Slovaquie",
    "slovenia" to "Slovénie",
    "southafrica" to "Afrique du Sud",
    "spain" to "Espagne",
    "sweden" to "Suède",
    "switzerland" to "Suisse",
    "tunisia" to "Tunisie",
    "turkey" to "Turquie",
    "turkiye" to "Turquie",
    "ukraine" to "Ukraine",
    "unitedstates" to "États-Unis",
    "usa" to "États-Unis",
    "uruguay" to "Uruguay",
    "wales" to "Pays de Galles",
)

private val replacementNames = nationalTeamNames.entries
    .map { entry -> entry.key to entry.value }
    .map { (canonical, translated) ->
        canonical.replace(Regex("(?=[A-Z])"), " ") to translated
    } + listOf(
        "United States" to "États-Unis",
        "USA" to "États-Unis",
        "Korea Republic" to "Corée du Sud",
        "South Korea" to "Corée du Sud",
        "New Zealand" to "Nouvelle-Zélande",
        "South Africa" to "Afrique du Sud",
        "Czech Republic" to "République tchèque",
        "Northern Ireland" to "Irlande du Nord",
        "Republic of Ireland" to "Irlande",
        "Ivory Coast" to "Côte d’Ivoire",
        "Cote d'Ivoire" to "Côte d’Ivoire",
    )
    .distinctBy { it.first.lowercase(Locale.FRANCE) }
    .sortedByDescending { it.first.length }
