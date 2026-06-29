package com.soliano.betvalueanalyzer.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import com.soliano.betvalueanalyzer.domain.PlayerStatProfile
import com.soliano.betvalueanalyzer.domain.StatSourceSnapshot
import com.soliano.betvalueanalyzer.domain.TeamProfileRequest
import com.soliano.betvalueanalyzer.domain.TeamStatProfile
import com.soliano.betvalueanalyzer.domain.teamProfileKey
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class OpenLigaStatisticsProvider(private val api: PublicSportsApiService) {
    private val cache = mutableMapOf<String, CachedProfile>()
    private val semaphore = Semaphore(4)

    suspend fun load(requests: List<TeamProfileRequest>): Map<String, TeamStatProfile> = coroutineScope {
        requests.filter { it.sport == "soccer" }
            .distinctBy { teamProfileKey(it.sport, it.teamName) }
            .take(30)
            .map { request ->
                async {
                    semaphore.withPermit {
                        val key = teamProfileKey(request.sport, request.teamName)
                        val cached = cache[key]?.takeIf { System.currentTimeMillis() - it.updatedAt < CACHE_MS }
                        val profile = cached?.profile ?: fetchProfile(request.teamName)?.also {
                            cache[key] = CachedProfile(it, System.currentTimeMillis())
                        }
                        key to profile
                    }
                }
            }.awaitAll().mapNotNull { (key, profile) -> profile?.let { key to it } }.toMap()
    }

    private suspend fun fetchProfile(teamName: String): TeamStatProfile? {
        val query = URLEncoder.encode(teamName, StandardCharsets.UTF_8.toString()).replace("+", "%20")
        val root = getArray("https://api.openligadb.de/getmatchesbyteam/$query/40/0") ?: return null
        val matches = root.mapNotNull { parseMatch(it.openLigaObject(), teamName) }
            .distinctBy { it.matchId }
            .sortedByDescending { it.date }
            .take(6)
        if (matches.size < 2) return null
        val wins = matches.count { it.scored > it.conceded }
        val draws = matches.count { it.scored == it.conceded }
        val losses = matches.size - wins - draws
        val averageScored = matches.map { it.scored }.average()
        val averageConceded = matches.map { it.conceded }.average()
        val playerGoals = matches.flatMap { it.scorers }.groupingBy { canonicalPlayer(it) }.eachCount()
        val playerNames = matches.flatMap { it.scorers }.associateBy { canonicalPlayer(it) }
        val players = playerGoals.map { (key, goals) ->
            PlayerStatProfile(
                name = playerNames.getValue(key),
                appearances = matches.size,
                starts = 0,
                goals = goals.toDouble(),
                sourceCount = 1,
                goalTrend = valueTrend(matches.map { match -> match.scorers.count { canonicalPlayer(it) == key }.toDouble() }),
                formTrend = valueTrend(matches.map { match -> match.scorers.count { canonicalPlayer(it) == key }.toDouble() }),
            )
        }
        return TeamStatProfile(
            teamName = teamName,
            games = matches.size,
            wins = wins,
            draws = draws,
            losses = losses,
            averageScored = averageScored,
            averageConceded = averageConceded,
            playerProfiles = players,
            sourceNames = listOf("OpenLigaDB"),
            sourceSnapshots = listOf(
                StatSourceSnapshot("OpenLigaDB", matches.size, wins, draws, losses, averageScored, averageConceded)
            ),
            sourceAgreement = 65,
            formTrend = teamTrend(matches),
        )
    }

    private fun parseMatch(item: JsonObject?, teamName: String): OpenLigaMatch? {
        item ?: return null
        if (item.openLigaBool("matchIsFinished") != true) return null
        val team1 = item.openLigaObject("team1") ?: return null
        val team2 = item.openLigaObject("team2") ?: return null
        val team1Name = team1.openLigaText("teamName") ?: return null
        val team2Name = team2.openLigaText("teamName") ?: return null
        val target = canonicalTeam(teamName)
        val isTeam1 = canonicalTeam(team1Name) == target || canonicalTeam(team1.openLigaText("shortName").orEmpty()) == target
        val isTeam2 = canonicalTeam(team2Name) == target || canonicalTeam(team2.openLigaText("shortName").orEmpty()) == target
        if (!isTeam1 && !isTeam2) return null
        val official = item.openLigaArray("matchResults").mapNotNull { it.openLigaObject() }
            .firstOrNull { it.openLigaInt("resultTypeID") == 2 }
            ?: return null
        val score1 = official.openLigaDouble("pointsTeam1") ?: return null
        val score2 = official.openLigaDouble("pointsTeam2") ?: return null
        val date = item.openLigaText("matchDateTimeUTC")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: return null
        val scorers = scorersForTeam(item, isTeam1)
        return OpenLigaMatch(
            matchId = item.openLigaText("matchID") ?: "$date:$team1Name:$team2Name",
            date = date,
            scored = if (isTeam1) score1 else score2,
            conceded = if (isTeam1) score2 else score1,
            scorers = scorers,
        )
    }

    private fun scorersForTeam(item: JsonObject, team1Target: Boolean): List<String> {
        var previous1 = 0
        var previous2 = 0
        return item.openLigaArray("goals").mapNotNull { it.openLigaObject() }
            .filter { goal ->
                goal.openLigaInt("matchMinute")?.let { it <= 90 } == true && goal.openLigaBool("isOvertime") != true
            }
            .sortedBy { it.openLigaInt("matchMinute") ?: Int.MAX_VALUE }
            .mapNotNull { goal ->
                val score1 = goal.openLigaInt("scoreTeam1") ?: previous1
                val score2 = goal.openLigaInt("scoreTeam2") ?: previous2
                val scoringTeam1 = score1 > previous1
                val scoringTeam2 = score2 > previous2
                previous1 = score1
                previous2 = score2
                if (goal.openLigaBool("isOwnGoal") == true) return@mapNotNull null
                val belongsToTarget = if (team1Target) scoringTeam1 else scoringTeam2
                goal.openLigaText("goalGetterName")?.takeIf { belongsToTarget && it.isNotBlank() }
            }
    }

    private suspend fun getArray(url: String): List<JsonElement>? {
        val response = runCatching { api.getRawUrl(url) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        return response.body()?.string()?.let { raw ->
            runCatching { JsonParser.parseString(raw).takeIf { it.isJsonArray }?.asJsonArray?.toList() }.getOrNull()
        }
    }

    private fun canonicalTeam(value: String): String {
        val plain = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), "")
            .replace("saintgermain", "sg")
        return plain.removePrefix("fc").removeSuffix("fc")
    }

    private fun canonicalPlayer(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), "")

    private fun teamTrend(matches: List<OpenLigaMatch>): Double {
        if (matches.size < 4) return 0.0
        val points = matches.map { when { it.scored > it.conceded -> 3.0; it.scored == it.conceded -> 1.0; else -> 0.0 } }
        return ((points.take(3).average() - points.drop(3).take(3).average()) / 3.0).coerceIn(-1.0, 1.0)
    }

    private fun valueTrend(valuesNewestFirst: List<Double>): Double {
        if (valuesNewestFirst.size < 4) return 0.0
        return (valuesNewestFirst.take(3).average() - valuesNewestFirst.drop(3).take(3).average()).coerceIn(-1.0, 1.0)
    }

    private data class OpenLigaMatch(
        val matchId: String,
        val date: Long,
        val scored: Double,
        val conceded: Double,
        val scorers: List<String>,
    )
    private data class CachedProfile(val profile: TeamStatProfile, val updatedAt: Long)
    private companion object { const val CACHE_MS = 30L * 60 * 1000 }
}

private fun JsonElement.openLigaObject(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.openLigaObject(name: String): JsonObject? = get(name)?.openLigaObject()
private fun JsonObject.openLigaArray(name: String): List<JsonElement> =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
private fun JsonObject.openLigaText(name: String): String? =
    runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()
private fun JsonObject.openLigaInt(name: String): Int? =
    runCatching { get(name)?.takeUnless { it.isJsonNull }?.asInt }.getOrNull()
private fun JsonObject.openLigaDouble(name: String): Double? =
    runCatching { get(name)?.takeUnless { it.isJsonNull }?.asDouble }.getOrNull()
private fun JsonObject.openLigaBool(name: String): Boolean? =
    runCatching { get(name)?.takeUnless { it.isJsonNull }?.asBoolean }.getOrNull()
