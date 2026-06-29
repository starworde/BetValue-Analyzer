package com.soliano.betvalueanalyzer.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import com.soliano.betvalueanalyzer.domain.CompetitionStandingSignal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset

class FifaCompetitionContextProvider(private val api: PublicSportsApiService) {
    suspend fun load(target: DeepAnalysisTarget): List<CompetitionStandingSignal> {
        if (!target.sportKey.startsWith("soccer")) return emptyList()
        val teamId = resolveTeamId(target.homeTeam) ?: return emptyList()
        val targetDate = Instant.ofEpochMilli(target.commenceTime).atZone(ZoneOffset.UTC).toLocalDate()
        val from = targetDate.minusDays(300)
        val to = targetDate.plusDays(180)
        val teamCalendar = getJson(
            "https://api.fifa.com/api/v3/calendar/matches?from=${from}T00%3A00%3A00Z&to=${to}T23%3A59%3A59Z&language=en&count=100&idTeam=$teamId"
        )?.standingArray("Results").orEmpty().mapNotNull { it.standingObject() }
        val match = teamCalendar.minByOrNull { item ->
            val names = listOfNotNull(item.standingObject("Home")?.standingTeamName(), item.standingObject("Away")?.standingTeamName())
            val pairPenalty = if (names.any { canonical(it) == canonical(target.awayTeam) }) 0L else 30L * DAY_MS
            val date = item.standingText("Date")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: Long.MAX_VALUE / 2
            kotlin.math.abs(date - target.commenceTime) + pairPenalty
        } ?: return emptyList()
        val seasonId = match.standingText("IdSeason") ?: return emptyList()
        val groupId = match.standingText("IdGroup")
        val stageId = match.standingText("IdStage") ?: return emptyList()
        val filter = if (!groupId.isNullOrBlank()) "idGroup=$groupId" else "idStage=$stageId"
        val competitionCalendar = getJson(
            "https://api.fifa.com/api/v3/calendar/matches?from=${from}T00%3A00%3A00Z&to=${to}T23%3A59%3A59Z&language=en&count=500&idSeason=$seasonId&$filter"
        )?.standingArray("Results").orEmpty().mapNotNull { it.standingObject() }
        return calculateStandings(competitionCalendar, target, groupId != null)
    }

    private fun calculateStandings(
        matches: List<JsonObject>,
        target: DeepAnalysisTarget,
        isGroup: Boolean,
    ): List<CompetitionStandingSignal> {
        val rows = linkedMapOf<String, StandingRow>()
        matches.forEach { match ->
            val home = match.standingObject("Home") ?: return@forEach
            val away = match.standingObject("Away") ?: return@forEach
            val homeName = home.standingTeamName() ?: return@forEach
            val awayName = away.standingTeamName() ?: return@forEach
            val homeRow = rows.getOrPut(canonical(homeName)) { StandingRow(homeName) }
            val awayRow = rows.getOrPut(canonical(awayName)) { StandingRow(awayName) }
            val date = match.standingText("Date")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: return@forEach
            val scoreHome = match.standingDouble("HomeTeamScore")
            val scoreAway = match.standingDouble("AwayTeamScore")
            if (match.standingInt("MatchStatus") == 0 && date < target.commenceTime && scoreHome != null && scoreAway != null) {
                homeRow.played += 1
                awayRow.played += 1
                homeRow.goalsFor += scoreHome.toInt()
                homeRow.goalsAgainst += scoreAway.toInt()
                awayRow.goalsFor += scoreAway.toInt()
                awayRow.goalsAgainst += scoreHome.toInt()
                when {
                    scoreHome > scoreAway -> homeRow.points += 3
                    scoreAway > scoreHome -> awayRow.points += 3
                    else -> { homeRow.points += 1; awayRow.points += 1 }
                }
            } else if (date >= target.commenceTime) {
                homeRow.remaining += 1
                awayRow.remaining += 1
            }
        }
        val table = rows.values.sortedWith(
            compareByDescending<StandingRow> { it.points }
                .thenByDescending { it.goalsFor - it.goalsAgainst }
                .thenByDescending { it.goalsFor }
        )
        if (table.size < 2) return emptyList()
        val leader = table.first().points
        return listOf(target.homeTeam, target.awayTeam).mapNotNull { teamName ->
            val index = table.indexOfFirst { canonical(it.name) == canonical(teamName) }
            if (index < 0) return@mapNotNull null
            val row = table[index]
            val position = index + 1
            val qualifyingPosition = if (isGroup) minOf(2, table.size) else 1
            val qualificationPoints = table.getOrNull(qualifyingPosition - 1)?.points ?: leader
            val gapQualification = (qualificationPoints - row.points).coerceAtLeast(0)
            val reachable = gapQualification <= row.remaining * 3
            val importance = when {
                row.remaining <= 2 && (position <= qualifyingPosition + 1 || reachable) -> 0.95
                row.remaining <= 5 && (position <= 3 || position >= table.size - 2) -> 0.75
                isGroup && position > qualifyingPosition && reachable -> 0.70
                position == 1 -> 0.45
                else -> 0.25
            }
            val description = when {
                isGroup && position <= qualifyingPosition ->
                    "${position}e sur ${table.size}, ${row.points} pts ; doit défendre une place qualificative (${row.remaining} match(s) restant(s))"
                isGroup && reachable ->
                    "${position}e sur ${table.size}, à $gapQualification pt(s) de la qualification ; résultat très important"
                position == 1 ->
                    "leader avec ${row.points} pts ; ${row.remaining} match(s) pour conserver la première place"
                position >= table.size - 2 ->
                    "${position}e sur ${table.size}, sous pression dans le bas du classement"
                else ->
                    "${position}e sur ${table.size}, à ${leader - row.points} pt(s) du leader, ${row.remaining} match(s) restant(s)"
            }
            CompetitionStandingSignal(
                teamName = teamName,
                position = position,
                teamCount = table.size,
                points = row.points,
                gapToLeader = leader - row.points,
                matchesRemaining = row.remaining,
                importance = importance,
                description = description,
            )
        }
    }

    private suspend fun resolveTeamId(teamName: String): String? {
        val term = URLEncoder.encode(teamName, StandardCharsets.UTF_8.toString())
        val candidates = getJson("https://api.fifa.com/api/v3/teams/search?name=$term&language=en&count=100")
            ?.standingArray("Results").orEmpty().mapNotNull { it.standingObject() }
            .filter { it.standingInt("FootballType") == 0 && it.standingInt("Gender") == 1 && it.standingInt("AgeType") == 7 }
        return candidates.mapNotNull { candidate ->
            val name = candidate.standingText("ShortClubName") ?: candidate.standingObjectName() ?: return@mapNotNull null
            if (canonical(name) != canonical(teamName)) return@mapNotNull null
            val id = candidate.standingText("IdTeam") ?: return@mapNotNull null
            val score = (if (candidate.standingInt("ActiveStatus") == 0) 10 else 0) + if (id.all(Char::isDigit)) 2 else 0
            id to score
        }.maxByOrNull { it.second }?.first
    }

    private suspend fun getJson(url: String): JsonObject? {
        val response = runCatching { api.getRawUrl(url) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        return response.body()?.string()?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
    }

    private fun canonical(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), "")
        .replace("saintgermain", "sg").removePrefix("fc").removeSuffix("fc")

    private data class StandingRow(
        val name: String,
        var played: Int = 0,
        var points: Int = 0,
        var goalsFor: Int = 0,
        var goalsAgainst: Int = 0,
        var remaining: Int = 0,
    )

    private companion object { const val DAY_MS = 24L * 60 * 60 * 1000 }
}

private fun JsonElement.standingObject(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.standingObject(name: String): JsonObject? = get(name)?.standingObject()
private fun JsonObject.standingArray(name: String): List<JsonElement> =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
private fun JsonObject.standingText(name: String): String? =
    runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()
private fun JsonObject.standingInt(name: String): Int? =
    runCatching { get(name)?.takeUnless { it.isJsonNull }?.asInt }.getOrNull()
private fun JsonObject.standingDouble(name: String): Double? =
    runCatching { get(name)?.takeUnless { it.isJsonNull }?.asDouble }.getOrNull()
private fun JsonObject.standingTeamName(): String? =
    standingText("ShortClubName") ?: standingArray("TeamName").firstOrNull()?.standingObject()?.standingText("Description")
private fun JsonObject.standingObjectName(): String? =
    standingArray("Name").firstOrNull()?.standingObject()?.standingText("Description")
