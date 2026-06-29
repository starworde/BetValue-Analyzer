package com.soliano.betvalueanalyzer.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import com.soliano.betvalueanalyzer.domain.CompetitionStandingSignal
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

class EspnCompetitionContextProvider(private val api: PublicSportsApiService) {
    suspend fun load(target: DeepAnalysisTarget): List<CompetitionStandingSignal> {
        val sport = target.sportKey.substringBefore('/')
        val league = when {
            target.competitionName.contains("WNBA", true) -> "wnba"
            target.competitionName.contains("NBA", true) -> "nba"
            target.competitionName.contains("MLB", true) -> "mlb"
            target.competitionName.contains("NHL", true) -> "nhl"
            target.competitionName.contains("NFL", true) -> "nfl"
            else -> return emptyList()
        }
        val year = Instant.ofEpochMilli(target.commenceTime).atZone(ZoneOffset.UTC).year
        val root = getJson("https://site.api.espn.com/apis/v2/sports/$sport/$league/standings?season=$year") ?: return emptyList()
        val groups = root.collectGroups()
        return listOf(target.homeTeam, target.awayTeam).mapNotNull { teamName ->
            val group = groups.firstOrNull { entries -> entries.any { canonical(it.teamName) == canonical(teamName) } }
                ?: return@mapNotNull null
            val ordered = group.sortedBy { it.seed }
            val index = ordered.indexOfFirst { canonical(it.teamName) == canonical(teamName) }
            if (index < 0) return@mapNotNull null
            val row = ordered[index]
            val games = row.wins + row.losses
            val seasonGames = when (league) { "nba" -> 82; "wnba" -> 44; "mlb" -> 162; "nhl" -> 82; "nfl" -> 17; else -> games }
            val remaining = (seasonGames - games).coerceAtLeast(0)
            val qualificationLine = when (league) { "nba", "wnba" -> minOf(8, ordered.size); "nhl" -> minOf(8, ordered.size); else -> minOf(6, ordered.size) }
            val importance = when {
                remaining <= 5 && row.seed in (qualificationLine - 2)..(qualificationLine + 2) -> 0.95
                remaining <= 12 && row.seed in (qualificationLine - 2)..(qualificationLine + 2) -> 0.78
                row.seed <= 2 -> 0.45
                else -> 0.28
            }
            val description = when {
                row.seed == 1 -> "1er, bilan ${row.wins}-${row.losses} ; doit conserver la tête (${remaining} match(s) restant(s))"
                row.seed <= qualificationLine -> "${row.seed}e, bilan ${row.wins}-${row.losses}, dans la zone qualificative ; ${remaining} match(s) restant(s)"
                else -> "${row.seed}e, bilan ${row.wins}-${row.losses}, à ${formatGamesBehind(row.gamesBehind)} de la zone de tête"
            }
            CompetitionStandingSignal(
                teamName = teamName,
                position = row.seed,
                teamCount = ordered.size,
                points = row.wins,
                gapToLeader = row.gamesBehind.roundToInt(),
                matchesRemaining = remaining,
                importance = importance,
                description = description,
            )
        }
    }

    private fun JsonObject.collectGroups(): List<List<StandingEntry>> = espnArray("children").mapNotNull outer@{ child ->
        val node = child.espnObject() ?: return@outer null
        val entries = node.espnObject("standings")?.espnArray("entries").orEmpty().mapNotNull inner@{ element ->
            val entry = element.espnObject() ?: return@inner null
            val team = entry.espnObject("team") ?: return@inner null
            val stats = entry.espnArray("stats").mapNotNull { it.espnObject() }
                .associateBy { it.espnText("name").orEmpty() }
            StandingEntry(
                teamName = team.espnText("displayName") ?: return@inner null,
                seed = stats["playoffSeed"]?.espnDouble("value")?.roundToInt()
                    ?: stats["rank"]?.espnDouble("value")?.roundToInt() ?: 99,
                wins = stats["wins"]?.espnDouble("value")?.roundToInt() ?: 0,
                losses = stats["losses"]?.espnDouble("value")?.roundToInt() ?: 0,
                gamesBehind = stats["gamesBehind"]?.espnDouble("value") ?: 0.0,
            )
        }
        entries.takeIf { it.isNotEmpty() }
    }

    private suspend fun getJson(url: String): JsonObject? {
        val response = runCatching { api.getRawUrl(url) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        return response.body()?.string()?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
    }

    private fun canonical(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), "")
    private fun formatGamesBehind(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
    private data class StandingEntry(val teamName: String, val seed: Int, val wins: Int, val losses: Int, val gamesBehind: Double)
}

private fun JsonElement.espnObject(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.espnObject(name: String): JsonObject? = get(name)?.espnObject()
private fun JsonObject.espnArray(name: String): List<JsonElement> = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
private fun JsonObject.espnText(name: String): String? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()
private fun JsonObject.espnDouble(name: String): Double? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asDouble }.getOrNull()
