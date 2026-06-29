package com.soliano.betvalueanalyzer.domain

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset

fun isNeutralVenueCompetition(
    sportKey: String,
    sportTitle: String,
    competitionName: String,
    eventName: String = "",
    homeTeam: String = "",
    awayTeam: String = "",
    commenceTime: Long = 0L,
): Boolean {
    val sport = sportKey.substringBefore('/')
    if (sport in setOf("racing", "cycling", "golf", "tennis", "mma", "boxing", "athletics", "nascar", "snooker", "darts")) return true
    val text = normalizedVenueText(listOf(sportTitle, competitionName, eventName).joinToString(" "))
    if (text.isBlank()) return false

    if ("top 14" in text && eventFallsInMonths(commenceTime, setOf(6, 7))) return true
    if (neutralCompetitionTokens.any { it in text }) {
        if (isWorldCupLike(text) && hostNationAppears(homeTeam, awayTeam)) return false
        return true
    }
    return false
}

fun neutralVenueSummary(
    sportKey: String,
    sportTitle: String,
    competitionName: String,
    eventName: String = "",
    homeTeam: String = "",
    awayTeam: String = "",
    commenceTime: Long = 0L,
): String? = if (isNeutralVenueCompetition(sportKey, sportTitle, competitionName, eventName, homeTeam, awayTeam, commenceTime)) {
    "Terrain neutre probable : aucun avantage domicile/extérieur appliqué."
} else {
    null
}

fun isVenueEdgeLine(value: String): Boolean {
    val text = normalizedVenueText(value)
    return "domicile exterieur" in text ||
        "avantage domicile" in text ||
        "recoit" in text ||
        "deplacement" in text ||
        "deplace" in text
}

private fun isWorldCupLike(text: String): Boolean =
    "coupe du monde" in text || "world cup" in text || "fifa world cup" in text

private fun hostNationAppears(homeTeam: String, awayTeam: String): Boolean {
    val participants = normalizedVenueText("$homeTeam $awayTeam")
    return listOf(
        "usa",
        "united states",
        "etats unis",
        "etatsunis",
        "canada",
        "mexico",
        "mexique",
    ).any { it in participants }
}

private fun eventFallsInMonths(commenceTime: Long, months: Set<Int>): Boolean {
    if (commenceTime <= 0L) return false
    return runCatching {
        Instant.ofEpochMilli(commenceTime).atZone(ZoneOffset.UTC).monthValue in months
    }.getOrDefault(false)
}

private val neutralCompetitionTokens = listOf(
    "finale",
    "final",
    "demi finale",
    "semi final",
    "semi-final",
    "coupe du monde",
    "world cup",
    "fifa",
    "euro",
    "uefa nations league",
    "nations league",
    "copa america",
    "gold cup",
    "afcon",
    "can ",
    "championships",
    "world championships",
    "tournoi",
    "tournament",
    "grand chelem",
    "grand slam",
    "roland garros",
    "wimbledon",
    "us open",
    "australian open",
)

private fun normalizedVenueText(value: String): String {
    val decomposed = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    return decomposed
        .replace("œ", "oe")
        .replace("æ", "ae")
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
