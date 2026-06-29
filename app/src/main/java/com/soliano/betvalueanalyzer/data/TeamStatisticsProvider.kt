package com.soliano.betvalueanalyzer.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import com.soliano.betvalueanalyzer.domain.TeamProfileRequest
import com.soliano.betvalueanalyzer.domain.TeamStatProfile
import com.soliano.betvalueanalyzer.domain.LineupPlayer
import com.soliano.betvalueanalyzer.domain.LineupStatus
import com.soliano.betvalueanalyzer.domain.PlayerStatProfile
import com.soliano.betvalueanalyzer.domain.TeamLineup
import com.soliano.betvalueanalyzer.domain.StatSourceSnapshot
import com.soliano.betvalueanalyzer.domain.teamProfileKey
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class TeamStatisticsProvider(private val api: PublicSportsApiService) {
    private val profiles = mutableMapOf<String, CachedProfile>()
    private val directories = mutableMapOf<String, Map<String, String>>()
    private val semaphore = Semaphore(4)

    suspend fun load(requests: List<TeamProfileRequest>): Map<String, TeamStatProfile> = coroutineScope {
        val unique = requests.distinctBy { teamProfileKey(it.sport, it.teamName) }.take(36)
        unique.map { request ->
            async {
                semaphore.withPermit {
                    val key = teamProfileKey(request.sport, request.teamName)
                    val cached = profiles[key]?.takeIf { System.currentTimeMillis() - it.updatedAt < CACHE_MS }
                    if (cached != null) return@withPermit key to cached.profile
                    val profile = fetchProfile(request)
                    if (profile != null) profiles[key] = CachedProfile(profile, System.currentTimeMillis())
                    key to profile
                }
            }
        }.awaitAll().mapNotNull { (key, profile) -> profile?.let { key to it } }.toMap()
    }

    private suspend fun fetchProfile(request: TeamProfileRequest): TeamStatProfile? {
        val resolvedLeague = when (request.sport) {
            "soccer" -> "all"
            "basketball" -> "wnba"
            "baseball" -> "mlb"
            else -> return null
        }
        val teamId = request.suggestedTeamId
            ?: resolveTeamId(request.sport, request.league, request.teamName)
            ?: return null
        val year = LocalDate.now(ZoneOffset.UTC).year
        val url = "https://site.web.api.espn.com/apis/site/v2/sports/${request.sport}/$resolvedLeague/teams/$teamId/schedule?season=$year"
        val raw = getRaw(url) ?: return null
        val matches = parseCompletedMatches(raw, teamId).take(6)
        if (matches.isEmpty()) return null

        val advanced = matches.take(6).mapNotNull { match ->
            fetchAdvanced(match, request.sport, resolvedLeague, teamId, request.teamName)
        }
        val wins = matches.count { it.scored > it.conceded }
        val draws = matches.count { it.scored == it.conceded }
        val losses = matches.size - wins - draws
        val averageScored = matches.map { it.scored }.average()
        val averageConceded = matches.map { it.conceded }.average()
        return TeamStatProfile(
            teamName = request.teamName,
            games = matches.size,
            wins = wins,
            draws = draws,
            losses = losses,
            averageScored = averageScored,
            averageConceded = averageConceded,
            averageShots = advanced.mapNotNull { it.shots }.averageOrNull(),
            averageShotsOnTarget = advanced.mapNotNull { it.shotsOnTarget }.averageOrNull(),
            averageCorners = advanced.mapNotNull { it.corners }.averageOrNull(),
            averagePossession = advanced.mapNotNull { it.possession }.averageOrNull(),
            recentLineup = advanced.firstNotNullOfOrNull { it.lineup },
            playerProfiles = aggregatePlayers(advanced),
            sourceNames = listOf("ESPN"),
            sourceSnapshots = listOf(
                StatSourceSnapshot("ESPN", matches.size, wins, draws, losses, averageScored, averageConceded)
            ),
            sourceAgreement = 65,
            formTrend = teamTrend(matches),
        )
    }

    private suspend fun resolveTeamId(sport: String, league: String, name: String): String? {
        val directoryLeague = when (sport) {
            "soccer" -> if (league.contains("world", true) || league.contains("606")) "fifa.world" else "fifa.world"
            "basketball" -> "wnba"
            "baseball" -> "mlb"
            else -> return null
        }
        val directoryKey = "$sport/$directoryLeague"
        val directory = directories[directoryKey] ?: run {
            val url = "https://site.web.api.espn.com/apis/site/v2/sports/$sport/$directoryLeague/teams?limit=100"
            val parsed = getRaw(url)?.let(::parseTeamDirectory).orEmpty()
            if (parsed.isNotEmpty()) directories[directoryKey] = parsed
            parsed
        }
        return directory[normalize(name)]
    }

    private suspend fun fetchAdvanced(
        match: RecentMatch,
        sport: String,
        defaultLeague: String,
        teamId: String,
        teamName: String,
    ): AdvancedStats? {
        val league = match.league.ifBlank { defaultLeague }
        val url = "https://site.web.api.espn.com/apis/site/v2/sports/$sport/$league/summary?event=${match.eventId}"
        val root = getRaw(url)?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() } ?: return null
        val teams = root.obj("boxscore")?.array("teams").orEmpty()
        val team = teams.mapNotNull { it.asObject() }.firstOrNull { item ->
            item.obj("team")?.text("id") == teamId || normalize(item.obj("team")?.text("displayName").orEmpty()) == normalize(teamName)
        }
        val stats = team?.array("statistics").orEmpty().mapNotNull { item ->
            val stat = item.asObject() ?: return@mapNotNull null
            val name = stat.text("name") ?: return@mapNotNull null
            val value = stat.number("value") ?: stat.text("displayValue")?.replace("%", "")?.toDoubleOrNull()
            value?.let { name to it }
        }.toMap()
        val rosterData = if (sport == "soccer") {
            parseSoccerRoster(root, teamId, teamName)
        } else {
            parseBoxscorePlayers(root, teamId, teamName)
        }
        if (stats.isEmpty() && rosterData.players.isEmpty() && rosterData.lineup == null) return null
        return AdvancedStats(
            shots = stats["totalShots"],
            shotsOnTarget = stats["shotsOnTarget"],
            corners = stats["wonCorners"],
            possession = stats["possessionPct"],
            lineup = rosterData.lineup,
            players = rosterData.players,
        )
    }

    private fun parseSoccerRoster(root: JsonObject, teamId: String, teamName: String): RosterData {
        val roster = root.array("rosters").mapNotNull { it.asObject() }.firstOrNull { item ->
            item.obj("team")?.text("id") == teamId || normalize(item.obj("team")?.text("displayName").orEmpty()) == normalize(teamName)
        } ?: return RosterData()
        val appearances = roster.array("roster").mapNotNull { item ->
            val entry = item.asObject() ?: return@mapNotNull null
            val athlete = entry.obj("athlete") ?: return@mapNotNull null
            val name = athlete.text("displayName") ?: return@mapNotNull null
            val values = entry.array("stats").mapNotNull stats@{ statElement ->
                val stat = statElement.asObject() ?: return@stats null
                val key = stat.text("name") ?: return@stats null
                val value = stat.number("value") ?: return@stats null
                key to value
            }.toMap()
            PlayerMatchStats(
                name = name,
                appeared = (values["appearances"] ?: 0.0) > 0.0,
                starter = entry.bool("starter") == true,
                position = entry.text("formationPlace"),
                jersey = entry.text("jersey"),
                goals = values["totalGoals"] ?: 0.0,
                assists = values["goalAssists"] ?: 0.0,
                shots = values["totalShots"] ?: 0.0,
                shotsOnTarget = values["shotsOnTarget"] ?: 0.0,
                minutes = values["minutes"] ?: values["minutesPlayed"] ?: 0.0,
            )
        }
        return RosterData(
            lineup = lineupFrom(appearances, roster.text("formation")),
            players = appearances.filter { it.appeared },
        )
    }

    private fun parseBoxscorePlayers(root: JsonObject, teamId: String, teamName: String): RosterData {
        val teamPlayers = root.obj("boxscore")?.array("players").orEmpty().mapNotNull { it.asObject() }.firstOrNull { item ->
            item.obj("team")?.text("id") == teamId || normalize(item.obj("team")?.text("displayName").orEmpty()) == normalize(teamName)
        } ?: return RosterData()
        val parsed = teamPlayers.array("statistics").flatMap { sectionElement ->
            val section = sectionElement.asObject() ?: return@flatMap emptyList()
            val keys = section.array("keys").mapNotNull { runCatching { it.asString }.getOrNull() }
            section.array("athletes").mapNotNull { athleteElement ->
                val entry = athleteElement.asObject() ?: return@mapNotNull null
                val athlete = entry.obj("athlete") ?: return@mapNotNull null
                val name = athlete.text("displayName") ?: return@mapNotNull null
                val stats = entry.array("stats").mapNotNull { runCatching { it.asString }.getOrNull() }
                val values = keys.zip(stats).toMap()
                val didNotPlay = entry.bool("didNotPlay") == true || stats.isEmpty()
                PlayerMatchStats(
                    name = name,
                    appeared = !didNotPlay,
                    starter = entry.bool("starter") == true,
                    position = entry.obj("position")?.text("abbreviation") ?: athlete.obj("position")?.text("abbreviation"),
                    jersey = athlete.text("jersey"),
                    goals = values.value("goals"),
                    assists = values.value("assists"),
                    shots = values.value("shotsTotal"),
                    points = values.value("points"),
                    rebounds = values.value("rebounds"),
                    hits = values.value("hits"),
                    homeRuns = values.value("homeRuns"),
                    minutes = values.minutesValue(),
                )
            }
        }.groupBy { normalize(it.name) }.map { (_, samePlayer) ->
            samePlayer.reduce { first, next ->
                first.copy(
                    appeared = first.appeared || next.appeared,
                    starter = first.starter || next.starter,
                    position = first.position ?: next.position,
                    jersey = first.jersey ?: next.jersey,
                    goals = first.goals + next.goals,
                    assists = first.assists + next.assists,
                    shots = first.shots + next.shots,
                    points = first.points + next.points,
                    rebounds = first.rebounds + next.rebounds,
                    hits = first.hits + next.hits,
                    homeRuns = first.homeRuns + next.homeRuns,
                    minutes = first.minutes + next.minutes,
                )
            }
        }
        return RosterData(
            lineup = lineupFrom(parsed, null),
            players = parsed.filter { it.appeared },
        )
    }

    private fun lineupFrom(players: List<PlayerMatchStats>, formation: String?): TeamLineup? {
        val starters = players.filter { it.starter }.map {
            LineupPlayer(name = it.name, position = it.position, jersey = it.jersey)
        }
        return starters.takeIf { it.isNotEmpty() }?.let {
            TeamLineup(formation = formation, players = it, status = LineupStatus.PROBABLE)
        }
    }

    private fun aggregatePlayers(matches: List<AdvancedStats>): List<PlayerStatProfile> {
        val names = matches.flatMap { it.players }.associateBy { normalize(it.name) }
        return names.mapNotNull { (key, reference) ->
            val series = matches.map { match -> match.players.firstOrNull { normalize(it.name) == key && it.appeared } }
            val performances = series.filterNotNull()
            val appearances = performances.size
            if (appearances == 0) return@mapNotNull null
            val goalTrend = valueTrend(series.map { it?.goals ?: 0.0 })
            val assistTrend = valueTrend(series.map { it?.assists ?: 0.0 })
            val minutesSeries = series.map { it?.minutes ?: 0.0 }
            PlayerStatProfile(
                name = reference.name,
                appearances = appearances,
                starts = performances.count { it.starter },
                goals = performances.sumOf { it.goals },
                assists = performances.sumOf { it.assists },
                shots = performances.sumOf { it.shots },
                shotsOnTarget = performances.sumOf { it.shotsOnTarget },
                points = performances.sumOf { it.points },
                rebounds = performances.sumOf { it.rebounds },
                hits = performances.sumOf { it.hits },
                homeRuns = performances.sumOf { it.homeRuns },
                goalTrend = goalTrend,
                assistTrend = assistTrend,
                formTrend = (goalTrend * 0.65 + assistTrend * 0.35).coerceIn(-1.0, 1.0),
                totalMinutes = performances.sumOf { it.minutes },
                lastMatchMinutes = minutesSeries.firstOrNull() ?: 0.0,
                averageMinutes = performances.map { it.minutes }.filter { it > 0.0 }.averageOrNull() ?: 0.0,
                heavyRecentLoadCount = fatigueSpikeCount(minutesSeries, performances.map { it.minutes }.filter { it > 0.0 }.averageOrNull() ?: 0.0),
            )
        }.sortedWith(compareByDescending<PlayerStatProfile> { it.starts }.thenByDescending { it.appearances })
    }

    private fun fatigueSpikeCount(minutesNewestFirst: List<Double>, seasonAverage: Double): Int {
        if (seasonAverage <= 0.0) return 0
        val absoluteSpike = if (seasonAverage >= 70.0) 110.0 else if (seasonAverage >= 28.0) 42.0 else seasonAverage + 18.0
        return minutesNewestFirst.take(3).count { minutes ->
            minutes > 0.0 && (minutes >= seasonAverage + 18.0 || minutes >= absoluteSpike)
        }
    }

    private fun teamTrend(matches: List<RecentMatch>): Double {
        if (matches.size < 4) return 0.0
        val points = matches.map { when { it.scored > it.conceded -> 3.0; it.scored == it.conceded -> 1.0; else -> 0.0 } }
        return ((points.take(3).average() - points.drop(3).take(3).average()) / 3.0).coerceIn(-1.0, 1.0)
    }

    private fun valueTrend(valuesNewestFirst: List<Double>): Double {
        if (valuesNewestFirst.size < 4) return 0.0
        return (valuesNewestFirst.take(3).average() - valuesNewestFirst.drop(3).take(3).average()).coerceIn(-1.0, 1.0)
    }

    private suspend fun getRaw(url: String): String? {
        val response = runCatching { api.getRawUrl(url) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        return response.body()?.string()
    }

    private fun parseTeamDirectory(json: String): Map<String, String> = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        val teams = root.array("sports").firstOrNull()?.asObject()
            ?.array("leagues")?.firstOrNull()?.asObject()
            ?.array("teams").orEmpty()
        buildMap {
            teams.forEach { wrapper ->
                val team = wrapper.asObject()?.obj("team") ?: return@forEach
                val id = team.text("id") ?: return@forEach
                listOfNotNull(team.text("displayName"), team.text("name"), team.text("shortDisplayName"))
                    .forEach { put(normalize(it), id) }
            }
        }
    }.getOrDefault(emptyMap())

    private fun parseCompletedMatches(json: String, teamId: String): List<RecentMatch> = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        root.array("events").mapNotNull { element ->
            val event = element.asObject() ?: return@mapNotNull null
            val competition = event.array("competitions").firstOrNull()?.asObject() ?: return@mapNotNull null
            if (competition.obj("status")?.obj("type")?.bool("completed") != true) return@mapNotNull null
            val competitors = competition.array("competitors").mapNotNull { it.asObject() }
            val mine = competitors.firstOrNull {
                it.text("id") == teamId || it.obj("team")?.text("id") == teamId
            } ?: return@mapNotNull null
            val opponent = competitors.firstOrNull { it !== mine } ?: return@mapNotNull null
            val scored = mine.obj("score")?.number("value") ?: mine.obj("score")?.text("displayValue")?.toDoubleOrNull() ?: return@mapNotNull null
            val conceded = opponent.obj("score")?.number("value") ?: opponent.obj("score")?.text("displayValue")?.toDoubleOrNull() ?: return@mapNotNull null
            RecentMatch(
                eventId = event.text("id") ?: return@mapNotNull null,
                league = event.obj("league")?.text("slug").orEmpty(),
                date = event.text("date").orEmpty(),
                scored = scored,
                conceded = conceded,
            )
        }.sortedByDescending { it.date }
    }.getOrDefault(emptyList())

    private fun normalize(value: String): String = value.lowercase()
        .replace("united states", "usa")
        .replace(Regex("[^a-z0-9à-ÿ]+"), "")

    private fun List<Double>.averageOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()
    private fun Map<String, String>.value(key: String): Double = this[key]
        ?.replace(",", ".")
        ?.substringBefore('-')
        ?.toDoubleOrNull()
        ?: 0.0
    private fun Map<String, String>.minutesValue(): Double =
        listOf("minutes", "min", "MIN", "timeOnIce", "time", "TOI")
            .firstNotNullOfOrNull { key -> this[key]?.parseMinutes() }
            ?: 0.0

    private fun String.parseMinutes(): Double? {
        val cleaned = trim().replace(",", ".")
        val parts = cleaned.split(':')
        if (parts.size >= 2) {
            val minutes = parts[0].toDoubleOrNull() ?: return null
            val seconds = parts[1].toDoubleOrNull() ?: 0.0
            return minutes + seconds / 60.0
        }
        return cleaned.substringBefore('-').toDoubleOrNull()
    }
    private data class CachedProfile(val profile: TeamStatProfile, val updatedAt: Long)
    private data class RecentMatch(val eventId: String, val league: String, val date: String, val scored: Double, val conceded: Double)
    private data class AdvancedStats(
        val shots: Double?,
        val shotsOnTarget: Double?,
        val corners: Double?,
        val possession: Double?,
        val lineup: TeamLineup?,
        val players: List<PlayerMatchStats>,
    )
    private data class RosterData(
        val lineup: TeamLineup? = null,
        val players: List<PlayerMatchStats> = emptyList(),
    )
    private data class PlayerMatchStats(
        val name: String,
        val appeared: Boolean,
        val starter: Boolean,
        val position: String? = null,
        val jersey: String? = null,
        val goals: Double = 0.0,
        val assists: Double = 0.0,
        val shots: Double = 0.0,
        val shotsOnTarget: Double = 0.0,
        val points: Double = 0.0,
        val rebounds: Double = 0.0,
        val hits: Double = 0.0,
        val homeRuns: Double = 0.0,
        val minutes: Double = 0.0,
    )
    private companion object { const val CACHE_MS = 30L * 60 * 1000 }
}

private fun JsonElement.asObject(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.obj(name: String): JsonObject? = get(name)?.asObject()
private fun JsonObject.array(name: String): List<JsonElement> = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
private fun JsonObject.text(name: String): String? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()
private fun JsonObject.number(name: String): Double? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asDouble }.getOrNull()
private fun JsonObject.bool(name: String): Boolean? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asBoolean }.getOrNull()
