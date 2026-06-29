package com.soliano.betvalueanalyzer.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import com.soliano.betvalueanalyzer.domain.StatSourceSnapshot
import com.soliano.betvalueanalyzer.domain.TeamProfileRequest
import com.soliano.betvalueanalyzer.domain.TeamStatProfile
import com.soliano.betvalueanalyzer.domain.teamProfileKey
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class TheSportsDbStatisticsProvider(private val api: PublicSportsApiService) {
    private val cache = mutableMapOf<String, CachedProfile>()
    private val semaphore = Semaphore(4)

    suspend fun load(requests: List<TeamProfileRequest>): Map<String, TeamStatProfile> = coroutineScope {
        requests.filter { expectedSportNames(it.sport).isNotEmpty() }
            .distinctBy { teamProfileKey(it.sport, it.teamName) }
            .take(30)
            .map { request ->
                async {
                    semaphore.withPermit {
                        val key = teamProfileKey(request.sport, request.teamName)
                        val cached = cache[key]?.takeIf { System.currentTimeMillis() - it.updatedAt < CACHE_MS }
                        val profile = cached?.profile ?: fetch(request)?.also {
                            cache[key] = CachedProfile(it, System.currentTimeMillis())
                        }
                        key to profile
                    }
                }
            }.awaitAll().mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
    }

    private suspend fun fetch(request: TeamProfileRequest): TeamStatProfile? {
        val term = URLEncoder.encode(request.teamName, StandardCharsets.UTF_8.toString())
        val search = getJson("https://www.thesportsdb.com/api/v1/json/123/searchteams.php?t=$term") ?: return null
        val expectedSports = expectedSportNames(request.sport).takeIf { it.isNotEmpty() } ?: return null
        val team = search.array("teams").mapNotNull { it.objOrNull() }.firstOrNull {
            it.text("strSport") in expectedSports && normalize(it.text("strTeam").orEmpty()) == normalize(request.teamName)
        } ?: return null
        val id = team.text("idTeam") ?: return null
        val results = getJson("https://www.thesportsdb.com/api/v1/json/123/eventslast.php?id=$id")
            ?.array("results").orEmpty().mapNotNull { parseMatch(it.objOrNull(), id, request.teamName, request.sport) }.take(6)
        if (results.isEmpty()) return null
        val wins = results.count { it.scored > it.conceded }
        val draws = results.count { it.scored == it.conceded }
        val losses = results.size - wins - draws
        val averageScored = results.map { it.scored }.average()
        val averageConceded = results.map { it.conceded }.average()
        return TeamStatProfile(
            teamName = request.teamName,
            games = results.size,
            wins = wins,
            draws = draws,
            losses = losses,
            averageScored = averageScored,
            averageConceded = averageConceded,
            sourceNames = listOf("TheSportsDB"),
            sourceSnapshots = listOf(
                StatSourceSnapshot("TheSportsDB", results.size, wins, draws, losses, averageScored, averageConceded)
            ),
            sourceAgreement = 65,
            formTrend = teamTrend(results),
        )
    }

    private fun parseMatch(item: JsonObject?, teamId: String, teamName: String, sport: String): MatchResult? {
        item ?: return null
        if (item.text("strStatus") !in setOf("FT", "Match Finished", "AET", null)) return null
        val homeScore = item.text("intHomeScore")?.toDoubleOrNull() ?: return null
        val awayScore = item.text("intAwayScore")?.toDoubleOrNull() ?: return null
        if (homeScore + awayScore > maxReasonableTotal(sport)) return null
        val isHome = item.text("idHomeTeam") == teamId || normalize(item.text("strHomeTeam").orEmpty()) == normalize(teamName)
        val isAway = item.text("idAwayTeam") == teamId || normalize(item.text("strAwayTeam").orEmpty()) == normalize(teamName)
        if (!isHome && !isAway) return null
        return MatchResult(if (isHome) homeScore else awayScore, if (isHome) awayScore else homeScore)
    }

    private suspend fun getJson(url: String): JsonObject? {
        val response = runCatching { api.getRawUrl(url) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        return response.body()?.string()?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
    }

    private fun normalize(value: String): String = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), "")

    private fun expectedSportNames(sport: String): Set<String> = when (sport.substringBefore('/')) {
        "soccer" -> setOf("Soccer")
        "basketball" -> setOf("Basketball")
        "baseball" -> setOf("Baseball")
        "rugby" -> setOf("Rugby", "Rugby Union", "Rugby League")
        "hockey" -> setOf("Ice Hockey", "Hockey")
        "football" -> setOf("American Football")
        "australian_football" -> setOf("Australian Rules Football", "Australian Football")
        "handball" -> setOf("Handball")
        "volleyball" -> setOf("Volleyball")
        "field_hockey" -> setOf("Field Hockey")
        "cricket" -> setOf("Cricket")
        else -> emptySet()
    }

    private fun maxReasonableTotal(sport: String): Double = when (sport.substringBefore('/')) {
        "soccer", "hockey", "field_hockey", "volleyball" -> 18.0
        "baseball" -> 45.0
        "rugby", "handball" -> 95.0
        "basketball", "football", "australian_football" -> 260.0
        "cricket" -> 800.0
        else -> 60.0
    }

    private fun teamTrend(results: List<MatchResult>): Double {
        if (results.size < 4) return 0.0
        fun points(result: MatchResult): Double = when {
            result.scored > result.conceded -> 3.0
            result.scored == result.conceded -> 1.0
            else -> 0.0
        }
        return ((results.take(3).map(::points).average() -
            results.drop(3).take(3).map(::points).average()) / 3.0).coerceIn(-1.0, 1.0)
    }

    private data class MatchResult(val scored: Double, val conceded: Double)
    private data class CachedProfile(val profile: TeamStatProfile, val updatedAt: Long)
    private companion object { const val CACHE_MS = 30L * 60 * 1000 }
}

private fun JsonElement.objOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.array(name: String): List<JsonElement> = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
private fun JsonObject.text(name: String): String? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()
