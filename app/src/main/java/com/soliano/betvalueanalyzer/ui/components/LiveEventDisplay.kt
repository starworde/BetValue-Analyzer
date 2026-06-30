package com.soliano.betvalueanalyzer.ui.components

import com.soliano.betvalueanalyzer.data.local.LiveEventEntity

fun LiveEventEntity.isResultBoardEvent(): Boolean =
    sportKey.substringBefore('/') in setOf("cycling", "racing", "nascar", "golf", "athletics")

fun LiveEventEntity.hasConfirmedScore(): Boolean =
    homeScore != null && awayScore != null

fun LiveEventEntity.hasLiveMainMetric(): Boolean =
    hasConfirmedScore() || resultBoardLine() != null

fun LiveEventEntity.liveMainMetricTag(): String =
    when {
        isResultBoardEvent() && resultBoardLine() != null && isLive -> "TOP 3 LIVE"
        isResultBoardEvent() && resultBoardLine() != null -> "CLASSEMENT"
        hasConfirmedScore() && isLive -> "SCORE LIVE"
        hasConfirmedScore() -> "SCORE FINAL"
        isLive -> "EN DIRECT"
        else -> "A SURVEILLER"
    }

fun LiveEventEntity.liveMainMetricLabel(): String =
    if (isResultBoardEvent()) "Top 3 / classement" else "Score réel"

fun LiveEventEntity.liveMainMetricText(): String =
    if (isResultBoardEvent()) {
        resultBoardLine()
            ?: if (isLive) "Classement officiel en attente" else "Classement officiel en attente"
    } else if (hasConfirmedScore()) {
        "${displayTeamName(homeName)} $homeScore — $awayScore ${displayTeamName(awayName)}"
    } else {
        "Score officiel en attente des flux publics"
    }

fun LiveEventEntity.liveProgressLabel(): String =
    when (sportKey.substringBefore('/')) {
        "cycling" -> "Km restants / course"
        "racing", "nascar" -> "Tours restants / session"
        "golf" -> "Round / trous"
        "athletics" -> "Epreuve / serie"
        else -> "Temps de jeu"
    }

fun LiveEventEntity.liveProgressText(): String {
    val sport = sportKey.substringBefore('/')
    val status = cleanDisplayText(statusDescription).trim()
    val clock = cleanDisplayText(displayClock).trim()
    val parts = buildList {
        if (isResultBoardEvent()) {
            resultProgressLine(sport)?.let { add(it) }
            if (clock.isNotBlank() && clock != "0:00") add(clock)
            period?.takeIf { it > 0 && isLive }?.let { add(resultPeriodLabel(sport, it)) }
            if (status.isNotBlank() && status.lowercase() !in setOf("scheduled", "status_scheduled")) add(status)
        } else {
            if (clock.isNotBlank()) add(clock)
            period?.takeIf { it > 0 }?.let { add(matchPeriodLabel(sport, it)) }
            if (status.isNotBlank()) add(status)
        }
    }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.liveResultLineKey() }

    return parts.joinToString(" · ").ifBlank {
        if (isResultBoardEvent()) "Avancement officiel en attente" else "Temps officiel en attente"
    }
}

fun LiveEventEntity.liveParticipantsText(): String =
    if (isResultBoardEvent()) {
        cleanDisplayText(eventName.ifBlank { homeName })
    } else {
        "${displayTeamName(homeName)} — ${displayTeamName(awayName)}"
    }

fun LiveEventEntity.resultBoardLine(): String? =
    statSummary.lines()
        .map { cleanDisplayText(it).trim() }
        .firstOrNull { line ->
            val normalized = line.liveResultLineKey()
            normalized.startsWith("top 3 / classement public") ||
                normalized.startsWith("classement public") ||
                normalized.startsWith("resultat course")
        }
        ?.substringAfter(':', missingDelimiterValue = "")
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.contains("attente", ignoreCase = true) }

private fun LiveEventEntity.resultProgressLine(sport: String): String? =
    (statSummary.lines() + statusDescription + displayClock)
        .map { cleanDisplayText(it).trim() }
        .firstNotNullOfOrNull { line -> line.extractResultProgressHint(sport) }

private fun String.extractResultProgressHint(sport: String): String? {
    val clean = cleanDisplayText(this).trim()
    if (clean.isBlank()) return null
    val normalized = clean.liveResultLineKey()
    val payload = clean.substringAfter(':', missingDelimiterValue = clean).trim()
    val lapPattern = Regex("""(?i)\b(?:lap|laps|tour|tours)\s*(\d{1,3})\s*/\s*(\d{1,3})""")
    lapPattern.find(clean)?.let { match ->
        val current = match.groupValues.getOrNull(1)?.toIntOrNull()
        val total = match.groupValues.getOrNull(2)?.toIntOrNull()
        if (current != null && total != null && total >= current) {
            val remaining = total - current
            return "Tour $current/$total · $remaining tours restants"
        }
    }
    val remainingLapsPattern = Regex("""(?i)(\d{1,3})\s*(?:laps?|tours?)\s*(?:remaining|restants?)""")
    remainingLapsPattern.find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { laps ->
        return "$laps tours restants"
    }
    val kmPattern = Regex("""(?i)(\d+(?:[,.]\d+)?)\s*(?:km|kilometres?)\s*(?:remaining|restants?|a parcourir|à parcourir)""")
    kmPattern.find(normalized)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { km ->
        return "$km km restants"
    }
    val holesPattern = Regex("""(?i)(\d{1,2})\s*(?:holes?|trous?)\s*(?:played|joues?|restants?)""")
    holesPattern.find(normalized)?.let { match ->
        val holes = match.groupValues.getOrNull(1).orEmpty()
        return if ("restant" in normalized) "$holes trous restants" else "$holes trous joués"
    }
    val hasProgressPrefix = normalized.startsWith("avancement course") ||
        normalized.startsWith("tours restants") ||
        normalized.startsWith("distance restante") ||
        normalized.startsWith("km restants") ||
        normalized.startsWith("kilometres restants") ||
        normalized.startsWith("round / trous") ||
        normalized.startsWith("tour / trous") ||
        normalized.startsWith("session suivie")
    if (hasProgressPrefix && payload.isNotBlank() && !payload.contains("attente", ignoreCase = true)) return payload
    if (sport in setOf("racing", "nascar") && (normalized.contains("lap") || normalized.contains("tour"))) return payload
    if (sport == "cycling" && (normalized.contains("km") || normalized.contains("distance"))) return payload
    if (sport == "golf" && (normalized.contains("round") || normalized.contains("trou") || normalized.contains("hole"))) return payload
    return null
}

private fun matchPeriodLabel(sport: String, period: Int): String =
    when (sport) {
        "soccer", "rugby", "handball" -> if (period <= 1) "1re periode" else "${period}e periode"
        "basketball" -> "quart-temps $period"
        "football" -> "quart-temps $period"
        "baseball" -> "manche $period"
        "hockey" -> "tiers-temps $period"
        "tennis" -> "set $period"
        "volleyball" -> "set $period"
        else -> "periode $period"
    }

private fun resultPeriodLabel(sport: String, period: Int): String =
    when (sport) {
        "racing", "nascar" -> "tour $period"
        "golf" -> "round $period"
        "cycling" -> "section $period"
        "athletics" -> "serie $period"
        else -> "periode $period"
    }

private fun String.liveResultLineKey(): String =
    lowercase()
        .replace('\u00e9', 'e')
        .replace('\u00e8', 'e')
        .replace('\u00ea', 'e')
        .replace('\u00eb', 'e')
        .replace('\u00e0', 'a')
        .replace('\u00e2', 'a')
        .replace('\u00f9', 'u')
        .replace('\u00fb', 'u')
        .replace('\u00ee', 'i')
        .replace('\u00ef', 'i')
        .replace('\u00f4', 'o')
        .replace('\u00e7', 'c')
