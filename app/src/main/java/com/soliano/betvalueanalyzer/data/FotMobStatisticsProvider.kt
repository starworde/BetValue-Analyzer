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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class FotMobStatisticsProvider(private val api: PublicSportsApiService) {
    private val cache = mutableMapOf<String, CachedProfile>()
    private val teamIds = mutableMapOf<String, String>()
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
        val teamId = resolveTeamId(teamName) ?: return null
        val root = getJson("https://www.fotmob.com/api/data/teams?id=$teamId") ?: return null
        val matches = root.obj("fixtures")?.obj("allFixtures")?.array("fixtures").orEmpty()
            .mapNotNull { parseMatch(it.objOrNull(), teamId, teamName) }
            .sortedByDescending { it.date }
            .take(6)
        if (matches.size < 2) return null
        val wins = matches.count { it.scored > it.conceded }
        val draws = matches.count { it.scored == it.conceded }
        val losses = matches.size - wins - draws
        val averageScored = matches.map { it.scored }.average()
        val averageConceded = matches.map { it.conceded }.average()
        val players = parseSquad(root)
        val coachName = root.obj("squad")?.array("squad").orEmpty()
            .flatMap { it.objOrNull()?.array("members").orEmpty() }
            .mapNotNull { it.objOrNull() }
            .firstOrNull { it.obj("role")?.text("key") == "coach" }
            ?.text("name")
        return TeamStatProfile(
            teamName = teamName,
            games = matches.size,
            wins = wins,
            draws = draws,
            losses = losses,
            averageScored = averageScored,
            averageConceded = averageConceded,
            playerProfiles = players,
            sourceNames = listOf("FotMob"),
            sourceSnapshots = listOf(
                StatSourceSnapshot("FotMob", matches.size, wins, draws, losses, averageScored, averageConceded)
            ),
            sourceAgreement = 65,
            coachName = coachName,
            formTrend = teamTrend(matches),
        )
    }

    private suspend fun resolveTeamId(teamName: String): String? {
        val key = normalize(teamName)
        teamIds[key]?.let { return it }
        val term = URLEncoder.encode(teamName, StandardCharsets.UTF_8.toString())
        val root = getElement("https://www.fotmob.com/api/data/search/suggest?term=$term") ?: return null
        val suggestions = root.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty().flatMap { section ->
            section.objOrNull()?.array("suggestions").orEmpty()
        }.mapNotNull { it.objOrNull() }
        val match = suggestions.firstOrNull {
            it.text("type") == "team" && normalize(it.text("name").orEmpty()) == key
        } ?: suggestions.firstOrNull {
            it.text("type") == "team" && normalize(it.text("name").orEmpty()).contains(key)
        }
        val id = match?.text("id") ?: return null
        teamIds[key] = id
        return id
    }

    private fun parseMatch(item: JsonObject?, teamId: String, teamName: String): RecentMatch? {
        item ?: return null
        val status = item.obj("status") ?: return null
        if (status.bool("finished") != true || status.bool("cancelled") == true) return null
        val home = item.obj("home") ?: return null
        val away = item.obj("away") ?: return null
        val homeScore = home.number("score") ?: return null
        val awayScore = away.number("score") ?: return null
        // FotMob peut inclure les tirs au but dans certains scores de coupe : ils ne doivent pas fausser la forme à 90 minutes.
        if (homeScore + awayScore > 10.0) return null
        val isHome = home.text("id") == teamId || normalize(home.text("name").orEmpty()) == normalize(teamName)
        val isAway = away.text("id") == teamId || normalize(away.text("name").orEmpty()) == normalize(teamName)
        if (!isHome && !isAway) return null
        return RecentMatch(
            date = status.text("utcTime").orEmpty(),
            scored = if (isHome) homeScore else awayScore,
            conceded = if (isHome) awayScore else homeScore,
        )
    }

    private fun parseSquad(root: JsonObject): List<PlayerStatProfile> =
        root.obj("squad")?.array("squad").orEmpty().flatMap { section ->
            section.objOrNull()?.array("members").orEmpty()
        }.mapNotNull { memberElement ->
            val member = memberElement.objOrNull() ?: return@mapNotNull null
            val name = member.text("name") ?: return@mapNotNull null
            if (member.obj("role")?.text("key") == "coach") return@mapNotNull null
            val injury = member.obj("injury")
            val availability = injury?.let {
                listOfNotNull(it.text("description"), it.text("expectedReturn")).joinToString(" · ").ifBlank { "Blessure signalée" }
            }
            PlayerStatProfile(
                name = name,
                appearances = 6,
                starts = 0,
                goals = member.number("goals") ?: 0.0,
                assists = member.number("assists") ?: 0.0,
                sourceCount = 1,
                availabilityNote = availability,
            )
        }

    private suspend fun getElement(url: String): JsonElement? {
        val response = runCatching { api.getRawUrl(url) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        return response.body()?.string()?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }
    }

    private suspend fun getJson(url: String): JsonObject? = getElement(url)?.objOrNull()

    private fun normalize(value: String): String = value.lowercase()
        .replace("united states", "usa")
        .replace(Regex("[^a-z0-9à-ÿ]+"), "")

    private fun teamTrend(matches: List<RecentMatch>): Double {
        if (matches.size < 4) return 0.0
        val points = matches.map { when { it.scored > it.conceded -> 3.0; it.scored == it.conceded -> 1.0; else -> 0.0 } }
        return ((points.take(3).average() - points.drop(3).take(3).average()) / 3.0).coerceIn(-1.0, 1.0)
    }

    private data class RecentMatch(val date: String, val scored: Double, val conceded: Double)
    private data class CachedProfile(val profile: TeamStatProfile, val updatedAt: Long)

    private companion object {
        const val CACHE_MS = 30L * 60 * 1000
    }
}

private fun JsonElement.objOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.obj(name: String): JsonObject? = get(name)?.objOrNull()
private fun JsonObject.array(name: String): List<JsonElement> = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
private fun JsonObject.text(name: String): String? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()
private fun JsonObject.number(name: String): Double? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asDouble }.getOrNull()
private fun JsonObject.bool(name: String): Boolean? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asBoolean }.getOrNull()
