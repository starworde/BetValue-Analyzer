package com.soliano.betvalueanalyzer.domain

fun canonicalCompetitionName(value: String): String = when (value.trim().lowercase()) {
    "fifa world cup", "world cup", "coupe du monde", "finalement coupe du monde" -> "Coupe du monde FIFA"
    "english premier league" -> "Premier League"
    "french ligue 1" -> "Ligue 1"
    "spanish laliga" -> "LaLiga"
    "italian serie a" -> "Serie A"
    "german bundesliga" -> "Bundesliga"
    else -> value.trim()
}

fun competitionFavoriteKey(sport: String, competitionName: String): String {
    val normalized = canonicalCompetitionName(competitionName).lowercase()
        .replace(Regex("[^a-z0-9à-ÿ]+"), "-")
        .trim('-')
    return "$sport:$normalized"
}
