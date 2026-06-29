package com.soliano.betvalueanalyzer.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import com.soliano.betvalueanalyzer.domain.LineupPlayer
import com.soliano.betvalueanalyzer.domain.LineupStatus
import com.soliano.betvalueanalyzer.domain.PlayerStatProfile
import com.soliano.betvalueanalyzer.domain.StatSourceSnapshot
import com.soliano.betvalueanalyzer.domain.TeamLineup
import com.soliano.betvalueanalyzer.domain.TeamProfileRequest
import com.soliano.betvalueanalyzer.domain.TeamStatProfile
import com.soliano.betvalueanalyzer.domain.teamProfileKey
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class FifaStatisticsProvider(
    private val api: PublicSportsApiService,
    private val historyDays: Long = 300,
    private val maxDetailMatches: Int = 6,
) {
    private val profiles = mutableMapOf<String, CachedProfile>()
    private val teamIds = mutableMapOf<String, String>()
    private val matchDetails = mutableMapOf<String, CachedDetail>()
    private val semaphore = Semaphore(4)
    private val detailSemaphore = Semaphore(6)

    suspend fun load(requests: List<TeamProfileRequest>): Map<String, TeamStatProfile> = coroutineScope {
        requests.filter { it.sport == "soccer" }
            .distinctBy { teamProfileKey(it.sport, it.teamName) }
            .take(30)
            .map { request ->
                async {
                    semaphore.withPermit {
                        val key = teamProfileKey(request.sport, request.teamName)
                        val cached = profiles[key]?.takeIf { System.currentTimeMillis() - it.updatedAt < CACHE_MS }
                        val profile = cached?.profile ?: fetchProfile(request.teamName)?.also {
                            profiles[key] = CachedProfile(it, System.currentTimeMillis())
                        }
                        key to profile
                    }
                }
            }.awaitAll().mapNotNull { (key, profile) -> profile?.let { key to it } }.toMap()
    }

    private suspend fun fetchProfile(teamName: String): TeamStatProfile? {
        val teamId = resolveTeamId(teamName) ?: return null
        val today = LocalDate.now(ZoneOffset.UTC)
        val from = today.minusDays(historyDays.coerceIn(90, 370)).format(DateTimeFormatter.ISO_DATE)
        val to = today.format(DateTimeFormatter.ISO_DATE)
        val url = "https://api.fifa.com/api/v3/calendar/matches?from=${from}T00%3A00%3A00Z&to=${to}T23%3A59%3A59Z&language=en&count=100&idTeam=$teamId"
        val seasonSummaries = getJson(url)?.fifaArray("Results").orEmpty()
            .mapNotNull { parseSummary(it.fifaObject(), teamId, teamName) }
            .sortedByDescending { it.date }
        val summaries = seasonSummaries.take(6)
        if (summaries.size < 2) return null

        val details = coroutineScope {
            seasonSummaries.take(maxDetailMatches.coerceIn(6, 24)).map { summary ->
                async { detailSemaphore.withPermit { loadDetail(summary.matchId, teamId, teamName) } }
            }.awaitAll().filterNotNull()
        }
        val wins = summaries.count { it.scored > it.conceded }
        val draws = summaries.count { it.scored == it.conceded }
        val losses = summaries.size - wins - draws
        val averageScored = summaries.map { it.scored }.average()
        val averageConceded = summaries.map { it.conceded }.average()
        return TeamStatProfile(
            teamName = teamName,
            games = summaries.size,
            wins = wins,
            draws = draws,
            losses = losses,
            averageScored = averageScored,
            averageConceded = averageConceded,
            recentLineup = details.firstNotNullOfOrNull { it.lineup },
            playerProfiles = aggregatePlayers(details, details.size),
            sourceNames = listOf("FIFA officiel"),
            sourceSnapshots = listOf(
                StatSourceSnapshot("FIFA officiel", summaries.size, wins, draws, losses, averageScored, averageConceded)
            ),
            sourceAgreement = 70,
            coachName = details.firstNotNullOfOrNull { it.coachName },
            formTrend = teamTrend(seasonSummaries),
        )
    }

    private suspend fun resolveTeamId(teamName: String): String? {
        val key = canonical(teamName)
        teamIds[key]?.let { return it }
        val term = URLEncoder.encode(teamName, StandardCharsets.UTF_8.toString())
        val candidates = getJson("https://api.fifa.com/api/v3/teams/search?name=$term&language=en&count=100")
            ?.fifaArray("Results").orEmpty().mapNotNull { it.fifaObject() }
            .filter {
                it.fifaInt("FootballType") == 0 && it.fifaInt("Gender") == 1 && it.fifaInt("AgeType") == 7
            }
        val match = candidates.mapNotNull { candidate ->
            val name = candidate.fifaText("ShortClubName")
                ?: candidate.fifaArray("Name").firstOrNull()?.fifaObject()?.fifaText("Description")
                ?: return@mapNotNull null
            val candidateKey = canonical(name)
            val similarity = when {
                candidateKey == key -> 100
                candidateKey.length >= 5 && (candidateKey.contains(key) || key.contains(candidateKey)) -> 65
                else -> return@mapNotNull null
            }
            val activeBonus = if (candidate.fifaInt("ActiveStatus") == 0) 20 else 0
            val id = candidate.fifaText("IdTeam") ?: return@mapNotNull null
            val stableIdBonus = if (id.all(Char::isDigit)) 5 else 0
            Triple(candidate, similarity + activeBonus + stableIdBonus, id)
        }.maxByOrNull { it.second } ?: return null
        teamIds[key] = match.third
        return match.third
    }

    private fun parseSummary(item: JsonObject?, teamId: String, teamName: String): MatchSummary? {
        item ?: return null
        if (item.fifaInt("MatchStatus") != 0) return null
        val date = item.fifaText("Date")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return null
        if (date > System.currentTimeMillis()) return null
        val home = item.fifaObject("Home") ?: return null
        val away = item.fifaObject("Away") ?: return null
        val homeName = home.fifaText("ShortClubName") ?: home.fifaLocalized("TeamName") ?: return null
        val awayName = away.fifaText("ShortClubName") ?: away.fifaLocalized("TeamName") ?: return null
        val isHome = home.fifaText("IdTeam") == teamId || canonical(homeName) == canonical(teamName)
        val isAway = away.fifaText("IdTeam") == teamId || canonical(awayName) == canonical(teamName)
        if (!isHome && !isAway) return null
        val homeScore = item.fifaDouble("HomeTeamScore") ?: home.fifaDouble("Score") ?: return null
        val awayScore = item.fifaDouble("AwayTeamScore") ?: away.fifaDouble("Score") ?: return null
        return MatchSummary(
            matchId = item.fifaText("IdMatch") ?: return null,
            date = date,
            scored = if (isHome) homeScore else awayScore,
            conceded = if (isHome) awayScore else homeScore,
        )
    }

    private suspend fun loadDetail(matchId: String, teamId: String, teamName: String): MatchDetail? {
        val now = System.currentTimeMillis()
        val root = matchDetails[matchId]?.takeIf { now - it.updatedAt < CACHE_MS }?.root ?: run {
            getJson("https://api.fifa.com/api/v3/live/football/$matchId?language=en")?.also {
                matchDetails[matchId] = CachedDetail(it, now)
            }
        } ?: return null
        val home = root.fifaObject("HomeTeam")
        val away = root.fifaObject("AwayTeam")
        val team = listOfNotNull(home, away).firstOrNull { candidate ->
            candidate.fifaText("IdTeam") == teamId || canonical(candidate.fifaText("ShortClubName").orEmpty()) == canonical(teamName)
        } ?: return null
        val substitutions = team.fifaArray("Substitutions").mapNotNull { it.fifaObject()?.fifaText("IdPlayerOn") }.toSet()
        val players = team.fifaArray("Players").mapNotNull { element ->
            val player = element.fifaObject() ?: return@mapNotNull null
            val id = player.fifaText("IdPlayer") ?: return@mapNotNull null
            val name = player.fifaLocalized("PlayerName") ?: player.fifaLocalized("ShortName") ?: return@mapNotNull null
            PlayerAppearance(
                id = id,
                name = name.smartCase(),
                appeared = player.fifaInt("Status") == 1 || id in substitutions,
                starter = player.fifaInt("Status") == 1,
                position = positionName(player.fifaInt("Position")),
                jersey = player.fifaText("ShirtNumber"),
            )
        }
        val goalsByPlayer = team.fifaArray("Goals").mapNotNull { it.fifaObject() }
            .filter { goal -> goal.fifaText("Minute")?.substringBefore("'")?.toIntOrNull()?.let { it <= 90 } == true }
            .groupingBy { it.fifaText("IdPlayer").orEmpty() }.eachCount()
        val assistsByPlayer = team.fifaArray("Goals").mapNotNull { it.fifaObject() }
            .filter { goal -> goal.fifaText("Minute")?.substringBefore("'")?.toIntOrNull()?.let { it <= 90 } == true }
            .mapNotNull { it.fifaText("IdAssistPlayer") }.groupingBy { it }.eachCount()
        val performances = players.filter { it.appeared }.map { player ->
            PlayerPerformance(
                name = player.name,
                starter = player.starter,
                goals = goalsByPlayer[player.id] ?: 0,
                assists = assistsByPlayer[player.id] ?: 0,
            )
        }
        val lineupPlayers = players.filter { it.starter }.map {
            LineupPlayer(it.name, it.position, it.jersey)
        }
        val coach = team.fifaArray("Coaches").mapNotNull { it.fifaObject() }
            .firstOrNull { it.fifaInt("Role") == 0 }?.fifaLocalized("Name")?.smartCase()
        return MatchDetail(
            lineup = lineupPlayers.takeIf { it.isNotEmpty() }?.let {
                TeamLineup(team.fifaText("Tactics"), it, LineupStatus.PROBABLE)
            },
            players = performances,
            coachName = coach,
        )
    }

    private fun aggregatePlayers(details: List<MatchDetail>, teamGames: Int): List<PlayerStatProfile> {
        val names = details.flatMap { it.players }.associateBy { canonical(it.name) }
        return names.map { (key, reference) ->
            val series = details.map { detail -> detail.players.firstOrNull { canonical(it.name) == key } }
            val goalTrend = valueTrend(series.map { it?.goals?.toDouble() ?: 0.0 })
            val assistTrend = valueTrend(series.map { it?.assists?.toDouble() ?: 0.0 })
            PlayerStatProfile(
                name = reference.name,
                appearances = series.count { it != null }.coerceAtMost(teamGames),
                starts = series.count { it?.starter == true },
                goals = series.sumOf { it?.goals ?: 0 }.toDouble(),
                assists = series.sumOf { it?.assists ?: 0 }.toDouble(),
                sourceCount = 1,
                goalTrend = goalTrend,
                assistTrend = assistTrend,
                formTrend = (goalTrend * 0.65 + assistTrend * 0.35).coerceIn(-1.0, 1.0),
            )
        }.sortedWith(compareByDescending<PlayerStatProfile> { it.starts }.thenByDescending { it.goals + it.assists })
    }

    private fun teamTrend(matches: List<MatchSummary>): Double {
        if (matches.size < 6) return 0.0
        val points = matches.map { when { it.scored > it.conceded -> 3.0; it.scored == it.conceded -> 1.0; else -> 0.0 } }
        val recent = points.take(4).average()
        val baseline = points.drop(4).average()
        val evidence = (points.drop(4).size / 10.0).coerceIn(0.35, 1.0)
        return ((recent - baseline) / 3.0 * evidence).coerceIn(-1.0, 1.0)
    }

    private fun valueTrend(valuesNewestFirst: List<Double>): Double {
        if (valuesNewestFirst.size < 6) return 0.0
        val recent = valuesNewestFirst.take(4).average()
        val baselineValues = valuesNewestFirst.drop(4)
        val evidence = (baselineValues.size / 10.0).coerceIn(0.30, 1.0)
        return ((recent - baselineValues.average()) * evidence).coerceIn(-1.0, 1.0)
    }

    private suspend fun getJson(url: String): JsonObject? {
        val response = runCatching { api.getRawUrl(url) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        return response.body()?.string()?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
    }

    private fun canonical(value: String): String {
        val plain = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), "")
            .replace("unitedstates", "usa")
            .replace("korearepublic", "southkorea")
            .replace("saintgermain", "sg")
        return plain.removePrefix("fc").removeSuffix("fc")
    }

    private fun String.smartCase(): String = split(' ').joinToString(" ") { token ->
        if (token.length <= 3 && token.all(Char::isUpperCase)) token.lowercase().replaceFirstChar(Char::uppercase)
        else token.lowercase().replaceFirstChar(Char::uppercase)
    }

    private fun positionName(value: Int?): String? = when (value) {
        0 -> "Gardien"
        1 -> "Défenseur"
        2 -> "Milieu"
        3 -> "Attaquant"
        else -> null
    }

    private data class MatchSummary(val matchId: String, val date: Long, val scored: Double, val conceded: Double)
    private data class PlayerAppearance(
        val id: String,
        val name: String,
        val appeared: Boolean,
        val starter: Boolean,
        val position: String?,
        val jersey: String?,
    )
    private data class PlayerPerformance(val name: String, val starter: Boolean, val goals: Int, val assists: Int)
    private data class MatchDetail(val lineup: TeamLineup?, val players: List<PlayerPerformance>, val coachName: String?)
    private data class CachedProfile(val profile: TeamStatProfile, val updatedAt: Long)
    private data class CachedDetail(val root: JsonObject, val updatedAt: Long)

    private companion object { const val CACHE_MS = 30L * 60 * 1000 }
}

private fun JsonElement.fifaObject(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.fifaObject(name: String): JsonObject? = get(name)?.fifaObject()
private fun JsonObject.fifaArray(name: String): List<JsonElement> =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
private fun JsonObject.fifaText(name: String): String? =
    runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()
private fun JsonObject.fifaInt(name: String): Int? =
    runCatching { get(name)?.takeUnless { it.isJsonNull }?.asInt }.getOrNull()
private fun JsonObject.fifaDouble(name: String): Double? =
    runCatching { get(name)?.takeUnless { it.isJsonNull }?.asDouble }.getOrNull()
private fun JsonObject.fifaLocalized(name: String): String? =
    fifaArray(name).mapNotNull { it.fifaObject()?.fifaText("Description") }.firstOrNull()
