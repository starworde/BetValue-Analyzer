package com.soliano.betvalueanalyzer.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import com.soliano.betvalueanalyzer.domain.LineupPlayer
import com.soliano.betvalueanalyzer.domain.LineupStatus
import com.soliano.betvalueanalyzer.domain.TeamLineup
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class MatchLineupRequest(
    val key: String,
    val sport: String,
    val league: String,
    val eventId: String,
    val homeTeam: String,
    val awayTeam: String,
)

data class MatchLineups(
    val home: TeamLineup? = null,
    val away: TeamLineup? = null,
)

class MatchLineupProvider(private val api: PublicSportsApiService) {
    private val cache = mutableMapOf<String, CachedLineups>()
    private val semaphore = Semaphore(3)

    suspend fun load(requests: List<MatchLineupRequest>): Map<String, MatchLineups> = coroutineScope {
        requests.distinctBy { it.key }.take(18).map { request ->
            async {
                semaphore.withPermit {
                    val cached = cache[request.key]?.takeIf { System.currentTimeMillis() - it.updatedAt < CACHE_MS }
                    val lineups = cached?.lineups ?: fetch(request).also {
                        cache[request.key] = CachedLineups(it, System.currentTimeMillis())
                    }
                    request.key to lineups
                }
            }
        }.awaitAll().toMap()
    }

    private suspend fun fetch(request: MatchLineupRequest): MatchLineups {
        val url = "https://site.web.api.espn.com/apis/site/v2/sports/${request.sport}/${request.league}/summary?event=${request.eventId}"
        val response = runCatching { api.getRawUrl(url) }.getOrNull() ?: return MatchLineups()
        if (!response.isSuccessful) return MatchLineups()
        val root = response.body()?.string()?.let {
            runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull()
        } ?: return MatchLineups()
        val available = parseRosters(root) + parseBoxscorePlayers(root)
        return MatchLineups(
            home = available[normalize(request.homeTeam)],
            away = available[normalize(request.awayTeam)],
        )
    }

    private fun parseRosters(root: JsonObject): Map<String, TeamLineup> = buildMap {
        root.array("rosters").mapNotNull { it.objectOrNull() }.forEach { roster ->
            val team = roster.obj("team")?.text("displayName") ?: return@forEach
            val players = roster.array("roster").mapNotNull { entryElement ->
                val entry = entryElement.objectOrNull() ?: return@mapNotNull null
                if (entry.bool("starter") != true) return@mapNotNull null
                val athlete = entry.obj("athlete") ?: return@mapNotNull null
                val name = athlete.text("displayName") ?: return@mapNotNull null
                LineupPlayer(
                    name = name,
                    position = entry.obj("position")?.text("abbreviation")
                        ?: athlete.obj("position")?.text("abbreviation")
                        ?: entry.text("formationPlace"),
                    jersey = entry.text("jersey") ?: athlete.text("jersey"),
                )
            }
            if (players.isNotEmpty()) {
                put(normalize(team), TeamLineup(roster.text("formation"), players, LineupStatus.OFFICIAL))
            }
        }
    }

    private fun parseBoxscorePlayers(root: JsonObject): Map<String, TeamLineup> = buildMap {
        root.obj("boxscore")?.array("players").orEmpty().mapNotNull { it.objectOrNull() }.forEach { teamEntry ->
            val team = teamEntry.obj("team")?.text("displayName") ?: return@forEach
            val players = teamEntry.array("statistics").flatMap { sectionElement ->
                val section = sectionElement.objectOrNull() ?: return@flatMap emptyList()
                section.array("athletes").mapNotNull { athleteElement ->
                    val entry = athleteElement.objectOrNull() ?: return@mapNotNull null
                    if (entry.bool("starter") != true) return@mapNotNull null
                    val athlete = entry.obj("athlete") ?: return@mapNotNull null
                    val name = athlete.text("displayName") ?: return@mapNotNull null
                    LineupPlayer(
                        name = name,
                        position = entry.obj("position")?.text("abbreviation")
                            ?: athlete.obj("position")?.text("abbreviation"),
                        jersey = athlete.text("jersey"),
                    )
                }
            }.distinctBy { normalize(it.name) }
            if (players.isNotEmpty()) {
                put(normalize(team), TeamLineup(players = players, status = LineupStatus.OFFICIAL))
            }
        }
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace("united states", "usa")
        .replace(Regex("[^a-z0-9à-ÿ]+"), "")

    private data class CachedLineups(val lineups: MatchLineups, val updatedAt: Long)

    private companion object {
        const val CACHE_MS = 3L * 60 * 1000
    }
}

private fun JsonElement.objectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.obj(name: String): JsonObject? = get(name)?.objectOrNull()
private fun JsonObject.array(name: String): List<JsonElement> = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
private fun JsonObject.text(name: String): String? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()
private fun JsonObject.bool(name: String): Boolean? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asBoolean }.getOrNull()
