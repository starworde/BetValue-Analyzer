package com.soliano.betvalueanalyzer.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import com.soliano.betvalueanalyzer.domain.StatSourceSnapshot
import com.soliano.betvalueanalyzer.domain.TeamProfileRequest
import com.soliano.betvalueanalyzer.domain.TeamStatProfile
import com.soliano.betvalueanalyzer.domain.teamProfileKey
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RugbyStatisticsProvider(
    private val api: PublicSportsApiService,
    private val historyDays: Long = 240,
    private val maxMatches: Int = 10,
) {
    private val mutex = Mutex()
    private var cached: CachedMatches? = null

    suspend fun load(requests: List<TeamProfileRequest>): Map<String, TeamStatProfile> {
        val rugbyRequests = requests.filter { it.sport == "rugby" }
            .distinctBy { teamProfileKey(it.sport, it.teamName) }
            .take(24)
        if (rugbyRequests.isEmpty()) return emptyMap()
        val matches = loadMatches()
        return rugbyRequests.mapNotNull { request ->
            val key = teamProfileKey(request.sport, request.teamName)
            buildProfile(request, matches)?.let { key to it }
        }.toMap()
    }

    private suspend fun loadMatches(): List<RugbyMatch> = mutex.withLock {
        cached?.takeIf { System.currentTimeMillis() - it.updatedAt < CACHE_MS }?.matches?.let { return@withLock it }
        val today = LocalDate.now(ZoneOffset.UTC)
        val dates = "${today.minusDays(historyDays).format(DateTimeFormatter.BASIC_ISO_DATE)}-${today.format(DateTimeFormatter.BASIC_ISO_DATE)}"
        val url = "https://site.web.api.espn.com/apis/site/v2/sports/rugby/all/scoreboard?dates=$dates&limit=700"
        val raw = runCatching { api.getRawUrl(url) }.getOrNull()
            ?.takeIf { it.isSuccessful }?.body()?.string()
        val matches = raw?.let(::parseMatches).orEmpty()
        cached = CachedMatches(matches, System.currentTimeMillis())
        matches
    }

    private fun parseMatches(json: String): List<RugbyMatch> = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        root.array("events").flatMap { eventElement ->
            val event = eventElement.asObject() ?: return@flatMap emptyList()
            val date = event.text("date").orEmpty()
            event.array("competitions").mapNotNull { competitionElement ->
                val competition = competitionElement.asObject() ?: return@mapNotNull null
                val completed = competition.obj("status")?.obj("type")?.bool("completed")
                    ?: event.obj("status")?.obj("type")?.bool("completed") ?: false
                if (!completed) return@mapNotNull null
                val competitors = competition.array("competitors").mapNotNull { it.asObject() }
                if (competitors.size < 2) return@mapNotNull null
                val first = competitors[0].toTeamScore() ?: return@mapNotNull null
                val second = competitors[1].toTeamScore() ?: return@mapNotNull null
                RugbyMatch(date, first, second)
            }
        }.sortedByDescending { it.date }
    }.getOrDefault(emptyList())

    private fun JsonObject.toTeamScore(): RugbyTeamScore? {
        val team = obj("team")
        val id = text("id") ?: team?.text("id")
        val name = team?.text("displayName") ?: team?.text("name") ?: return null
        val score = text("score")?.toDoubleOrNull()
            ?: obj("score")?.number("value")
            ?: obj("score")?.text("displayValue")?.toDoubleOrNull()
            ?: return null
        return RugbyTeamScore(id, name, score)
    }

    private fun buildProfile(request: TeamProfileRequest, matches: List<RugbyMatch>): TeamStatProfile? {
        val relevant = matches.mapNotNull { match ->
            val mine = match.participants.firstOrNull { participant ->
                request.suggestedTeamId?.let { it == participant.id } == true ||
                    normalize(participant.name) == normalize(request.teamName)
            } ?: return@mapNotNull null
            val opponent = match.participants.firstOrNull { it !== mine } ?: return@mapNotNull null
            RecentRugbyResult(match.date, mine.score, opponent.score)
        }.take(maxMatches)
        if (relevant.size < 2) return null
        val wins = relevant.count { it.scored > it.conceded }
        val draws = relevant.count { it.scored == it.conceded }
        val losses = relevant.size - wins - draws
        val averageScored = relevant.map { it.scored }.average()
        val averageConceded = relevant.map { it.conceded }.average()
        return TeamStatProfile(
            teamName = request.teamName,
            games = relevant.size,
            wins = wins,
            draws = draws,
            losses = losses,
            averageScored = averageScored,
            averageConceded = averageConceded,
            sourceNames = listOf("ESPN Rugby"),
            sourceSnapshots = listOf(
                StatSourceSnapshot("ESPN Rugby", relevant.size, wins, draws, losses, averageScored, averageConceded)
            ),
            sourceAgreement = 64,
            formTrend = trend(relevant),
        )
    }

    private fun trend(matchesNewestFirst: List<RecentRugbyResult>): Double {
        if (matchesNewestFirst.size < 4) return 0.0
        fun points(match: RecentRugbyResult): Double = when {
            match.scored > match.conceded -> 3.0
            match.scored == match.conceded -> 1.0
            else -> 0.0
        }
        return ((matchesNewestFirst.take(3).map(::points).average() -
            matchesNewestFirst.drop(3).take(3).map(::points).average()) / 3.0).coerceIn(-1.0, 1.0)
    }

    private fun normalize(value: String): String = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), "")

    private data class RugbyMatch(val date: String, val home: RugbyTeamScore, val away: RugbyTeamScore) {
        val participants: List<RugbyTeamScore> get() = listOf(home, away)
    }
    private data class RugbyTeamScore(val id: String?, val name: String, val score: Double)
    private data class RecentRugbyResult(val date: String, val scored: Double, val conceded: Double)
    private data class CachedMatches(val matches: List<RugbyMatch>, val updatedAt: Long)

    private companion object { const val CACHE_MS = 30L * 60 * 1000 }
}

private fun JsonElement.asObject(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.obj(name: String): JsonObject? = get(name)?.asObject()
private fun JsonObject.array(name: String): List<JsonElement> =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
private fun JsonObject.text(name: String): String? = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
}.getOrNull()
private fun JsonObject.number(name: String): Double? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asDouble }.getOrNull()
private fun JsonObject.bool(name: String): Boolean? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asBoolean }.getOrNull()
