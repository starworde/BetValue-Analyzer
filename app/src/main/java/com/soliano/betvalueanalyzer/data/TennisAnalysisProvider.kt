package com.soliano.betvalueanalyzer.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

data class TennisAnalysisSnapshot(
    val target: DeepAnalysisTarget,
    val playerA: TennisPlayerSnapshot,
    val playerB: TennisPlayerSnapshot,
    val h2h: TennisH2hSummary,
    val surface: String,
    val bestOf: Int,
    val sources: List<String>,
)

data class TennisPlayerSnapshot(
    val name: String,
    val tour: String,
    val espnId: String?,
    val rank: Int?,
    val previousRank: Int?,
    val rankingPoints: Int?,
    val rankingMove: Int?,
    val country: String?,
    val age: Int?,
    val careerWins: Int?,
    val careerLosses: Int?,
    val titles: Int?,
    val recentMatches: List<TennisRecentMatch>,
    val detailedMatches: List<TennisDetailedMatch>,
) {
    val recentCompleted: List<TennisRecentMatch> get() = recentMatches.filter { it.won != null }
    val recentWins: Int get() = recentCompleted.count { it.won == true }
    val recentLosses: Int get() = recentCompleted.count { it.won == false }
    val recentWinRate: Double? get() = recentCompleted.takeIf { it.isNotEmpty() }?.let { recentWins.toDouble() / it.size }
}

data class TennisRecentMatch(
    val id: String,
    val date: Long,
    val tournament: String,
    val round: String,
    val opponent: String,
    val opponentId: String?,
    val won: Boolean?,
    val surface: String,
    val note: String?,
)

data class TennisH2hSummary(
    val matches: List<TennisRecentMatch>,
    val playerAWins: Int,
    val playerBWins: Int,
    val bySurface: Map<String, Pair<Int, Int>>,
)

data class TennisDetailedMatch(
    val id: String,
    val date: Long,
    val tournament: String,
    val round: String,
    val opponent: String,
    val won: Boolean,
    val surface: String,
    val score: String,
    val bestOf: Int?,
    val minutes: Int?,
    val playerRank: Int?,
    val playerRankPoints: Int?,
    val playerCountry: String?,
    val playerAge: Double?,
    val playerHand: String?,
    val opponentRank: Int?,
    val opponentRankPoints: Int?,
    val aces: Int?,
    val doubleFaults: Int?,
    val servicePoints: Int?,
    val firstServeIn: Int?,
    val firstServeWon: Int?,
    val secondServeWon: Int?,
    val serviceGames: Int?,
    val breakPointsSaved: Int?,
    val breakPointsFaced: Int?,
    val opponentBreakPointsSaved: Int?,
    val opponentBreakPointsFaced: Int?,
)

class TennisAnalysisProvider(private val api: PublicSportsApiService) {
    private var rankingsCache: CachedRankings? = null
    private val tmlRowsCache = mutableMapOf<String, CachedTmlRows>()
    private val eventNameCache = mutableMapOf<String, String>()

    suspend fun load(
        target: DeepAnalysisTarget,
        onProgress: (Double, String) -> Unit,
    ): TennisAnalysisSnapshot {
        onProgress(0.08, "Recherche classements ATP/WTA et identifiants joueurs")
        val rankings = loadRankings()
        val aRanking = rankings.findPlayer(target.homeTeam, target.competitionName)
        val bRanking = rankings.findPlayer(target.awayTeam, target.competitionName)

        val currentYear = Instant.ofEpochMilli(target.commenceTime).atZone(ZoneOffset.UTC).year
        onProgress(0.24, "Lecture historique 365 jours : surface, aces, service, retour")
        val tmlRows = loadTmlRows(currentYear, target.commenceTime)
        val playerA = buildPlayerSnapshot(target.homeTeam, aRanking, currentYear, target.commenceTime, tmlRows)
        onProgress(0.46, "Historique ${target.homeTeam} consolide")
        val playerB = buildPlayerSnapshot(target.awayTeam, bRanking, currentYear, target.commenceTime, tmlRows)

        onProgress(0.66, "Calcul forme, surface et confrontations directes")
        val surface = inferSurface(target.competitionName, "", "", indoor = false)
        val h2h = buildH2h(playerA, playerB)
        val sources = buildList {
            add("ESPN rankings ATP/WTA")
            add("ESPN core athlete statistics")
            add("ESPN eventlog tennis 365 jours")
            add("ESPN competition details surface/H2H")
            if (tmlRows.isNotEmpty()) add("TennisMyLife ATP match stats CSV : surface, aces, service, retour, H2H")
            add("ATP/WTA officiel : rankings, profils joueurs, ordre de jeu et infos tournoi")
        }.distinct()
        return TennisAnalysisSnapshot(
            target = target,
            playerA = playerA,
            playerB = playerB,
            h2h = h2h,
            surface = surface,
            bestOf = bestOf(target),
            sources = sources,
        )
    }

    private suspend fun buildPlayerSnapshot(
        name: String,
        ranking: TennisRankingEntry?,
        currentYear: Int,
        targetTime: Long,
        tmlRows: List<TmlMatchRow>,
    ): TennisPlayerSnapshot {
        val stats = ranking?.id?.let { fetchCareerStats(it) }
        val detailed = tmlRows
            .mapNotNull { it.toDetailedMatch(name, ranking?.displayName, targetTime) }
            .filter { targetTime - it.date in 0..TENNIS_LOOKBACK_MS }
            .distinctBy { it.id }
            .sortedByDescending { it.date }
            .take(48)
        val detailedRecent = detailed.map { it.toRecentMatch() }
        val matches = ranking?.id?.let { id ->
            listOf(currentYear, currentYear - 1)
                .flatMap { year -> fetchEventLog(id, year, targetTime) }
                .distinctBy { it.id }
                .sortedByDescending { it.date }
                .take(32)
        }.orEmpty()
        val mergedMatches = (detailedRecent + matches)
            .distinctBy { it.matchIdentity() }
            .sortedByDescending { it.date }
            .take(48)
        val latestDetailed = detailed.firstOrNull()
        return TennisPlayerSnapshot(
            name = ranking?.displayName ?: name,
            tour = ranking?.tour ?: if (detailed.isNotEmpty()) "ATP" else tourFromName(name),
            espnId = ranking?.id,
            rank = ranking?.rank ?: latestDetailed?.playerRank,
            previousRank = ranking?.previousRank,
            rankingPoints = ranking?.points ?: latestDetailed?.playerRankPoints,
            rankingMove = ranking?.rankingMove ?: detailed.rankingMove(),
            country = ranking?.country ?: latestDetailed?.playerCountry,
            age = ranking?.age ?: latestDetailed?.playerAge?.roundToInt(),
            careerWins = stats?.singlesWon ?: detailed.count { it.won }.takeIf { it > 0 },
            careerLosses = stats?.singlesLost ?: detailed.count { !it.won }.takeIf { it > 0 },
            titles = stats?.singlesTitles,
            recentMatches = mergedMatches,
            detailedMatches = detailed,
        )
    }

    private suspend fun loadRankings(): List<TennisRankingEntry> {
        val cached = rankingsCache?.takeIf { System.currentTimeMillis() - it.updatedAt < 6 * 60 * 60 * 1000L }
        if (cached != null) return cached.rankings
        val rankings = listOf("atp", "wta").flatMap { league -> fetchRanking(league) }
        rankingsCache = CachedRankings(rankings, System.currentTimeMillis())
        return rankings
    }

    private suspend fun fetchRanking(league: String): List<TennisRankingEntry> {
        val root = getJson("https://site.web.api.espn.com/apis/site/v2/sports/tennis/$league/rankings") ?: return emptyList()
        val tour = league.uppercase(Locale.ROOT)
        return root.array("rankings")
            .flatMap { ranking -> ranking.objOrNull()?.array("ranks").orEmpty() }
            .mapNotNull { item ->
                val row = item.objOrNull() ?: return@mapNotNull null
                val athlete = row.obj("athlete") ?: return@mapNotNull null
                val current = row.int("current") ?: return@mapNotNull null
                val previous = row.int("previous")
                TennisRankingEntry(
                    id = athlete.text("id") ?: return@mapNotNull null,
                    displayName = athlete.text("displayName") ?: return@mapNotNull null,
                    shortName = athlete.text("shortname"),
                    tour = tour,
                    rank = current,
                    previousRank = previous,
                    points = row.number("points")?.roundToInt(),
                    trend = row.text("trend"),
                    country = athlete.text("citizenshipCountry") ?: athlete.text("flagAltText"),
                    age = athlete.int("age"),
                )
            }
    }

    private suspend fun fetchCareerStats(playerId: String): TennisCareerStats? {
        val root = getJson("https://sports.core.api.espn.com/v2/sports/tennis/athletes/$playerId/statistics?lang=en&region=us") ?: return null
        val stats = root.obj("splits")
            ?.array("categories").orEmpty()
            .flatMap { it.objOrNull()?.array("stats").orEmpty() }
            .mapNotNull { item ->
                val stat = item.objOrNull() ?: return@mapNotNull null
                val name = stat.text("name") ?: return@mapNotNull null
                name to (stat.number("value")?.roundToInt() ?: stat.text("displayValue")?.filter(Char::isDigit)?.toIntOrNull())
            }.toMap()
        return TennisCareerStats(
            singlesWon = stats["singlesWon"],
            singlesLost = stats["singlesLost"],
            singlesTitles = stats["singlesTitles"],
        )
    }

    private suspend fun fetchEventLog(playerId: String, season: Int, targetTime: Long): List<TennisRecentMatch> {
        val url = "https://sports.core.api.espn.com/v2/sports/tennis/athletes/$playerId/eventlog?season=$season&limit=75&lang=en&region=us"
        val root = getJson(url) ?: return emptyList()
        val rows = root.obj("events")?.array("items").orEmpty()
        val output = mutableListOf<TennisRecentMatch>()
        for (rowElement in rows.take(18)) {
            val row = rowElement.objOrNull() ?: continue
            if (row.bool("played") == false) continue
            val competitionRef = row.obj("competition")?.text("\$ref") ?: continue
            val competition = getJson(competitionRef.httpsRef()) ?: continue
            val date = competition.text("date")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: continue
            if (date >= targetTime) continue
            val match = competition.toRecentMatch(playerId, row.obj("event")?.text("\$ref")) ?: continue
            output += match.copy(date = date)
            delay(35)
        }
        return output
    }

    private suspend fun loadTmlRows(currentYear: Int, targetTime: Long): List<TmlMatchRow> {
        val years = listOf(currentYear, currentYear - 1).distinct()
        val yearRows = years.flatMap { year -> fetchTmlRows("$year", TML_YEAR_URL.format(Locale.US, year)) }
        val ongoingRows = fetchTmlRows("ongoing", TML_ONGOING_URL)
            .filter { row -> dateToMillis(row.tournamentDate)?.let { it < targetTime } == true }
        return (ongoingRows + yearRows)
            .distinctBy { "${it.tournamentId}:${it.matchNum}:${it.winnerName}:${it.loserName}" }
    }

    private suspend fun fetchTmlRows(cacheKey: String, url: String): List<TmlMatchRow> {
        val cached = tmlRowsCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.updatedAt < 8 * 60 * 60 * 1000L }
        if (cached != null) return cached.rows
        val csv = getText(url) ?: return emptyList()
        val rows = parseTmlCsv(csv)
        tmlRowsCache[cacheKey] = CachedTmlRows(rows, System.currentTimeMillis())
        return rows
    }

    private suspend fun JsonObject.toRecentMatch(playerId: String, eventRef: String?): TennisRecentMatch? {
        val competitors = array("competitors").mapNotNull { it.objOrNull() }
        val self = competitors.firstOrNull { it.text("id") == playerId || it.text("uid")?.endsWith("a:$playerId") == true } ?: return null
        val opponent = competitors.firstOrNull { it !== self && it.text("id") != "0" && !isPlaceholder(it.text("name").orEmpty()) } ?: return null
        val tournamentId = text("tournamentId").orEmpty()
        val tournamentName = eventRef?.let { eventName(it.httpsRef()) }.orEmpty()
            .ifBlank { tournamentNameFromId(tournamentId) }
            .ifBlank { "Tournoi tennis" }
        val round = obj("round")?.text("description").orEmpty().ifBlank { "Tour" }
        val venue = obj("venue")?.obj("address")?.text("summary").orEmpty()
        val court = obj("court")?.text("description").orEmpty()
        val indoor = obj("venue")?.bool("indoor") == true
        val note = array("notes").firstOrNull()?.objOrNull()?.text("text")
        return TennisRecentMatch(
            id = text("id") ?: "${tournamentId}-${self.text("id")}-${opponent.text("id")}-${text("date")}",
            date = 0L,
            tournament = tournamentName,
            round = round,
            opponent = opponent.text("name").orEmpty(),
            opponentId = opponent.text("id"),
            won = self.bool("winner"),
            surface = inferSurface(tournamentName, venue, court, indoor),
            note = note,
        )
    }

    private suspend fun eventName(ref: String): String {
        eventNameCache[ref]?.let { return it }
        val name = getJson(ref)?.let { root ->
            root.text("name") ?: root.text("shortName")
        }.orEmpty()
        if (name.isNotBlank()) eventNameCache[ref] = name
        return name
    }

    private fun buildH2h(a: TennisPlayerSnapshot, b: TennisPlayerSnapshot): TennisH2hSummary {
        val bKey = normalizeName(b.name)
        val aKey = normalizeName(a.name)
        val direct = (a.recentMatches.filter { normalizeName(it.opponent) == bKey || it.opponentId == b.espnId } +
            b.recentMatches.filter { normalizeName(it.opponent) == aKey || it.opponentId == a.espnId })
            .distinctBy { it.matchIdentity() }
            .sortedByDescending { it.date }
        val aWins = direct.count { match ->
            when {
                normalizeName(match.opponent) == bKey || match.opponentId == b.espnId -> match.won == true
                else -> match.won == false
            }
        }
        val bWins = direct.count { match ->
            when {
                normalizeName(match.opponent) == bKey || match.opponentId == b.espnId -> match.won == false
                else -> match.won == true
            }
        }
        val bySurface = direct.groupBy { it.surface }.mapValues { (_, matches) ->
            val surfaceAWins = matches.count { match ->
                if (normalizeName(match.opponent) == bKey || match.opponentId == b.espnId) match.won == true else match.won == false
            }
            surfaceAWins to (matches.size - surfaceAWins)
        }
        return TennisH2hSummary(direct, aWins, bWins, bySurface)
    }

    private suspend fun getJson(url: String): JsonObject? {
        val response = runCatching { api.getRawUrl(url) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        val body = response.body()?.string() ?: return null
        return runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
    }

    private suspend fun getText(url: String): String? {
        val response = runCatching { api.getRawUrl(url) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        return response.body()?.string()
    }

    private fun List<TennisRankingEntry>.findPlayer(name: String, competition: String): TennisRankingEntry? {
        val preferredTour = when {
            competition.contains("wta", true) || competition.contains("women", true) || competition.contains("women's", true) -> "WTA"
            competition.contains("atp", true) || competition.contains("men", true) || competition.contains("men's", true) -> "ATP"
            else -> null
        }
        val candidates = if (preferredTour != null) sortedBy { if (it.tour == preferredTour) 0 else 1 } else this
        val target = normalizeName(name)
        candidates.firstOrNull { normalizeName(it.displayName) == target }?.let { return it }
        candidates.firstOrNull { normalizeName(it.shortName.orEmpty()) == target }?.let { return it }
        val tokens = playerTokens(name)
        if (tokens.size >= 2) {
            val firstInitial = tokens.first().firstOrNull()
            val last = tokens.last()
            candidates.firstOrNull { entry ->
                val entryTokens = playerTokens(entry.displayName)
                entryTokens.isNotEmpty() && entryTokens.last() == last && entryTokens.first().firstOrNull() == firstInitial
            }?.let { return it }
        }
        return candidates.firstOrNull { entry ->
            val entryKey = normalizeName(entry.displayName)
            target.length > 5 && (entryKey.contains(target) || target.contains(entryKey))
        }
    }

    private fun bestOf(target: DeepAnalysisTarget): Int {
        val text = "${target.competitionName} ${target.sportKey}".lowercase(Locale.ROOT)
        val grandSlam = listOf("wimbledon", "roland garros", "french open", "us open", "australian open").any { it in text }
        val women = "wta" in text || "women" in text || "women's" in text
        return if (grandSlam && !women) 5 else 3
    }

    private fun tourFromName(name: String): String = if (name.isBlank()) "Tennis" else "Tennis"

    private fun isPlaceholder(value: String): Boolean {
        val normalized = value.trim().lowercase(Locale.ROOT)
        return normalized in setOf("bye", "tbd", "qualifier", "unknown") || normalized.startsWith("winner ")
    }

    private data class CachedRankings(val rankings: List<TennisRankingEntry>, val updatedAt: Long)

    private companion object {
        private const val TML_YEAR_URL = "https://raw.githubusercontent.com/Tennismylife/TML-Database/master/%d.csv"
        private const val TML_ONGOING_URL = "https://raw.githubusercontent.com/Tennismylife/TML-Database/master/ongoing_tourneys.csv"
        private const val TENNIS_LOOKBACK_MS = 370L * 24 * 60 * 60 * 1000
    }
}

private data class TennisRankingEntry(
    val id: String,
    val displayName: String,
    val shortName: String?,
    val tour: String,
    val rank: Int,
    val previousRank: Int?,
    val points: Int?,
    val trend: String?,
    val country: String?,
    val age: Int?,
) {
    val rankingMove: Int? get() = previousRank?.let { it - rank }
}

private data class TennisCareerStats(
    val singlesWon: Int?,
    val singlesLost: Int?,
    val singlesTitles: Int?,
)

private data class TmlMatchRow(
    val tournamentId: String,
    val tournamentName: String,
    val surface: String,
    val indoor: String,
    val tournamentDate: String,
    val matchNum: String,
    val winnerName: String,
    val winnerHand: String?,
    val winnerCountry: String?,
    val winnerAge: Double?,
    val winnerRank: Int?,
    val winnerRankPoints: Int?,
    val loserName: String,
    val loserHand: String?,
    val loserCountry: String?,
    val loserAge: Double?,
    val loserRank: Int?,
    val loserRankPoints: Int?,
    val score: String,
    val bestOf: Int?,
    val round: String,
    val minutes: Int?,
    val winnerStats: TennisRawMatchStats,
    val loserStats: TennisRawMatchStats,
) {
    fun toDetailedMatch(queryName: String, displayName: String?, targetTime: Long): TennisDetailedMatch? {
        val queryCandidates = listOf(queryName, displayName.orEmpty()).filter { it.isNotBlank() }
        val winnerIsPlayer = queryCandidates.any { samePlayerName(winnerName, it) }
        val loserIsPlayer = queryCandidates.any { samePlayerName(loserName, it) }
        if (!winnerIsPlayer && !loserIsPlayer) return null
        val date = dateToMillis(tournamentDate) ?: return null
        if (date >= targetTime) return null
        val playerStats = if (winnerIsPlayer) winnerStats else loserStats
        val opponentStats = if (winnerIsPlayer) loserStats else winnerStats
        val playerName = if (winnerIsPlayer) winnerName else loserName
        val opponentName = if (winnerIsPlayer) loserName else winnerName
        return TennisDetailedMatch(
            id = "tml:$tournamentId:$matchNum:${normalizeName(playerName)}",
            date = date,
            tournament = tournamentName.ifBlank { "Tournoi ATP" },
            round = round.ifBlank { "Tour" },
            opponent = opponentName,
            won = winnerIsPlayer,
            surface = tmlSurface(surface, indoor),
            score = score,
            bestOf = bestOf,
            minutes = minutes,
            playerRank = if (winnerIsPlayer) winnerRank else loserRank,
            playerRankPoints = if (winnerIsPlayer) winnerRankPoints else loserRankPoints,
            playerCountry = if (winnerIsPlayer) winnerCountry else loserCountry,
            playerAge = if (winnerIsPlayer) winnerAge else loserAge,
            playerHand = if (winnerIsPlayer) winnerHand else loserHand,
            opponentRank = if (winnerIsPlayer) loserRank else winnerRank,
            opponentRankPoints = if (winnerIsPlayer) loserRankPoints else winnerRankPoints,
            aces = playerStats.aces,
            doubleFaults = playerStats.doubleFaults,
            servicePoints = playerStats.servicePoints,
            firstServeIn = playerStats.firstServeIn,
            firstServeWon = playerStats.firstServeWon,
            secondServeWon = playerStats.secondServeWon,
            serviceGames = playerStats.serviceGames,
            breakPointsSaved = playerStats.breakPointsSaved,
            breakPointsFaced = playerStats.breakPointsFaced,
            opponentBreakPointsSaved = opponentStats.breakPointsSaved,
            opponentBreakPointsFaced = opponentStats.breakPointsFaced,
        )
    }
}

private data class TennisRawMatchStats(
    val aces: Int?,
    val doubleFaults: Int?,
    val servicePoints: Int?,
    val firstServeIn: Int?,
    val firstServeWon: Int?,
    val secondServeWon: Int?,
    val serviceGames: Int?,
    val breakPointsSaved: Int?,
    val breakPointsFaced: Int?,
)

private data class TennisDetailedStats(
    val matches: Int,
    val wins: Int,
    val losses: Int,
    val acesPerMatch: Double?,
    val doubleFaultsPerMatch: Double?,
    val firstServeInPct: Double?,
    val firstServeWonPct: Double?,
    val secondServeWonPct: Double?,
    val holdPct: Double?,
    val breakPointsSavedPct: Double?,
    val breakPointsCreatedPerMatch: Double?,
    val breakPointsConvertedPct: Double?,
    val minutesAvg: Double?,
)

private data class CachedTmlRows(val rows: List<TmlMatchRow>, val updatedAt: Long)

private fun parseTmlCsv(csv: String): List<TmlMatchRow> {
    val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
    if (lines.size < 2) return emptyList()
    val headers = parseCsvLine(lines.first()).mapIndexed { index, name -> name to index }.toMap()
    fun List<String>.value(name: String): String = getOrNull(headers[name] ?: -1).orEmpty()
    fun List<String>.int(name: String): Int? = value(name).toIntOrNull()
    fun List<String>.double(name: String): Double? = value(name).toDoubleOrNull()
    fun List<String>.stats(prefix: String): TennisRawMatchStats = TennisRawMatchStats(
        aces = int("${prefix}_ace"),
        doubleFaults = int("${prefix}_df"),
        servicePoints = int("${prefix}_svpt"),
        firstServeIn = int("${prefix}_1stIn"),
        firstServeWon = int("${prefix}_1stWon"),
        secondServeWon = int("${prefix}_2ndWon"),
        serviceGames = int("${prefix}_SvGms"),
        breakPointsSaved = int("${prefix}_bpSaved"),
        breakPointsFaced = int("${prefix}_bpFaced"),
    )
    return lines.drop(1).mapNotNull { rawLine ->
        val row = parseCsvLine(rawLine)
        if (row.size < headers.size.coerceAtMost(12)) return@mapNotNull null
        val winner = row.value("winner_name")
        val loser = row.value("loser_name")
        if (winner.isBlank() || loser.isBlank()) return@mapNotNull null
        TmlMatchRow(
            tournamentId = row.value("tourney_id"),
            tournamentName = row.value("tourney_name"),
            surface = row.value("surface"),
            indoor = row.value("indoor"),
            tournamentDate = row.value("tourney_date"),
            matchNum = row.value("match_num"),
            winnerName = winner,
            winnerHand = row.value("winner_hand").ifBlank { null },
            winnerCountry = row.value("winner_ioc").ifBlank { null },
            winnerAge = row.double("winner_age"),
            winnerRank = row.int("winner_rank"),
            winnerRankPoints = row.int("winner_rank_points"),
            loserName = loser,
            loserHand = row.value("loser_hand").ifBlank { null },
            loserCountry = row.value("loser_ioc").ifBlank { null },
            loserAge = row.double("loser_age"),
            loserRank = row.int("loser_rank"),
            loserRankPoints = row.int("loser_rank_points"),
            score = row.value("score"),
            bestOf = row.int("best_of"),
            round = row.value("round"),
            minutes = row.int("minutes"),
            winnerStats = row.stats("w"),
            loserStats = row.stats("l"),
        )
    }
}

private fun parseCsvLine(line: String): List<String> {
    val values = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var index = 0
    while (index < line.length) {
        val char = line[index]
        when {
            char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                current.append('"')
                index++
            }
            char == '"' -> quoted = !quoted
            char == ',' && !quoted -> {
                values += current.toString()
                current.setLength(0)
            }
            else -> current.append(char)
        }
        index++
    }
    values += current.toString()
    return values
}

private fun dateToMillis(value: String): Long? {
    if (value.length != 8) return null
    return runCatching {
        LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

private fun tmlSurface(surface: String, indoor: String): String {
    val normalized = normalizeName("$surface $indoor")
    return when {
        "i" == indoor.lowercase(Locale.ROOT) && "hard" in normalized -> "Dur indoor"
        "grass" in normalized -> "Gazon"
        "clay" in normalized -> "Terre battue"
        "carpet" in normalized -> "Dur indoor"
        "hard" in normalized -> "Dur"
        else -> surface.ifBlank { "Surface inconnue" }
    }
}

private fun samePlayerName(candidate: String, query: String): Boolean {
    val a = playerTokens(candidate)
    val b = playerTokens(query)
    if (a.isEmpty() || b.isEmpty()) return false
    if (a == b) return true
    val sameLast = a.last() == b.last()
    val sameInitial = a.first().firstOrNull() == b.first().firstOrNull()
    if (sameLast && sameInitial) return true
    val candidateKey = a.joinToString(" ")
    val queryKey = b.joinToString(" ")
    return queryKey.length >= 8 && candidateKey.length >= 8 && (candidateKey.contains(queryKey) || queryKey.contains(candidateKey))
}

private fun TennisDetailedMatch.toRecentMatch(): TennisRecentMatch = TennisRecentMatch(
    id = id,
    date = date,
    tournament = tournament,
    round = round,
    opponent = opponent,
    opponentId = null,
    won = won,
    surface = surface,
    note = score,
)

private fun TennisRecentMatch.matchIdentity(): String =
    listOf(date / (24 * 60 * 60 * 1000L), normalizeName(tournament), normalizeName(opponent)).joinToString("|")

private fun List<TennisDetailedMatch>.rankingMove(): Int? {
    val newest = firstOrNull { it.playerRank != null }?.playerRank ?: return null
    val oldest = lastOrNull { it.playerRank != null }?.playerRank ?: return null
    return oldest - newest
}

private fun TennisPlayerSnapshot.detailedStats(surface: String? = null): TennisDetailedStats? {
    val matches = detailedMatches
        .filter { surface == null || it.surface == surface }
        .takeIf { it.isNotEmpty() } ?: return null
    val servicePoints = matches.sumOf { it.servicePoints ?: 0 }.takeIf { it > 0 }
    val firstIn = matches.sumOf { it.firstServeIn ?: 0 }
    val firstWon = matches.sumOf { it.firstServeWon ?: 0 }
    val secondServePoints = servicePoints?.let { (it - firstIn).coerceAtLeast(0) }
    val secondWon = matches.sumOf { it.secondServeWon ?: 0 }
    val serviceGames = matches.sumOf { it.serviceGames ?: 0 }
    val breaksConceded = matches.sumOf { ((it.breakPointsFaced ?: 0) - (it.breakPointsSaved ?: 0)).coerceAtLeast(0) }
    val breakPointsFaced = matches.sumOf { it.breakPointsFaced ?: 0 }
    val breakPointsSaved = matches.sumOf { it.breakPointsSaved ?: 0 }
    val breakPointsCreated = matches.sumOf { it.opponentBreakPointsFaced ?: 0 }
    val breakPointsConverted = matches.sumOf { ((it.opponentBreakPointsFaced ?: 0) - (it.opponentBreakPointsSaved ?: 0)).coerceAtLeast(0) }
    return TennisDetailedStats(
        matches = matches.size,
        wins = matches.count { it.won },
        losses = matches.count { !it.won },
        acesPerMatch = matches.sumOf { it.aces ?: 0 }.takeIf { it > 0 }?.toDouble()?.div(matches.size),
        doubleFaultsPerMatch = matches.sumOf { it.doubleFaults ?: 0 }.takeIf { it > 0 }?.toDouble()?.div(matches.size),
        firstServeInPct = servicePoints?.let { firstIn.toDouble() / it },
        firstServeWonPct = firstIn.takeIf { it > 0 }?.let { firstWon.toDouble() / it },
        secondServeWonPct = secondServePoints?.takeIf { it > 0 }?.let { secondWon.toDouble() / it },
        holdPct = serviceGames.takeIf { it > 0 }?.let { ((it - breaksConceded).toDouble() / it).coerceIn(0.0, 1.0) },
        breakPointsSavedPct = breakPointsFaced.takeIf { it > 0 }?.let { breakPointsSaved.toDouble() / it },
        breakPointsCreatedPerMatch = breakPointsCreated.takeIf { it > 0 }?.toDouble()?.div(matches.size),
        breakPointsConvertedPct = breakPointsCreated.takeIf { it > 0 }?.let { breakPointsConverted.toDouble() / it },
        minutesAvg = matches.mapNotNull { it.minutes }.takeIf { it.isNotEmpty() }?.average(),
    )
}

fun TennisAnalysisSnapshot.toPredictionEntity(updateTime: Long): com.soliano.betvalueanalyzer.data.local.PredictionEntity {
    val score = tennisProjectionScore(playerA, playerB, surface, h2h)
    val probability = logistic(score).coerceIn(0.38, 0.82)
    val selection = if (probability >= 0.5) playerA.name else playerB.name
    val selectedProbability = if (probability >= 0.5) probability else 1.0 - probability
    val confidence = tennisConfidence(playerA, playerB, h2h)
    val expected = expectedTennisScore(selection, selectedProbability, bestOf)
    val playerALines = playerA.summaryLines(surface)
    val playerBLines = playerB.summaryLines(surface)
    val h2hLines = h2h.summaryLines(playerA.name, playerB.name)
    val context = listOf(
        "${playerA.name} : Aucun fait relevé",
        "${playerB.name} : Aucun fait relevé",
    )
    return com.soliano.betvalueanalyzer.data.local.PredictionEntity(
        id = "${target.id}:deep:tennis",
        eventId = "${target.id}:deep",
        sportKey = if ('/' in target.sportKey) target.sportKey else "tennis/deep",
        sportTitle = target.sportTitle.ifBlank { "Tennis" },
        competitionName = target.competitionName,
        commenceTime = target.commenceTime,
        homeTeam = playerA.name,
        awayTeam = playerB.name,
        market = "Vainqueur tennis",
        selection = selection,
        referenceOdds = 1.0 / selectedProbability.coerceIn(0.01, 0.99),
        impliedProbability = 0.0,
        consensusProbability = selectedProbability,
        valueEdge = 0.0,
        expectedValue = 0.0,
        confidenceScore = confidence,
        riskLevel = when {
            selectedProbability >= 0.68 && confidence >= 72 -> "Faible"
            selectedProbability >= 0.56 -> "Modéré"
            else -> "Élevé"
        },
        category = when {
            confidence >= 74 && selectedProbability >= 0.64 -> "Safe"
            confidence >= 58 -> "Mitigé"
            else -> "Exotique"
        },
        bookmakerCount = 0,
        sourceName = "Analyse tennis multi-source",
        sourceLastUpdate = updateTime,
        explanation = "Projection tennis calculée avec classement ATP/WTA, forme récente, surface, charge de matchs, service/retour et historique direct quand les données publiques existent.",
        positiveArguments = listOf(
            "Surface retenue : $surface.",
            "Fenêtre historique : 365 jours de résultats et statistiques de match quand la source les expose.",
        ).joinToString("\n"),
        negativeArguments = listOf(
            "Aucun fait relevé si aucune alerte joueur fiable n'est trouvée.",
            "Si un joueur n'est pas dans les bases publiques, l'app garde le match mais baisse la confiance.",
        ).joinToString("\n"),
        expectedScore = expected,
        statSummary = (playerALines + playerBLines + h2hLines).distinct().joinToString("\n"),
        scenarios = tennisScenarios(playerA, playerB, h2h, surface, bestOf, selection, selectedProbability).joinToString("\n") {
            "${it.first}|${it.second}|${"%.4f".format(Locale.US, it.third)}"
        },
        homeLineupStatus = "",
        homeLineup = "",
        awayLineupStatus = "",
        awayLineup = "",
        playerScenarios = tennisPlayerScenarios(playerA, playerB).joinToString("\n") {
            "${it.first}|${it.second}|${"%.4f".format(Locale.US, it.third)}"
        },
        sourceDetails = sources.joinToString("\n"),
        contextInsights = context.joinToString("\n"),
        sourceAgreement = tennisSourceAgreement(playerA, playerB, h2h),
    )
}

private fun tennisProjectionScore(
    a: TennisPlayerSnapshot,
    b: TennisPlayerSnapshot,
    surface: String,
    h2h: TennisH2hSummary,
): Double {
    var score = 0.0
    val rankA = a.rank
    val rankB = b.rank
    if (rankA != null && rankB != null) {
        score += ((rankB - rankA).toDouble() / 120.0).coerceIn(-1.25, 1.25)
    }
    val formA = a.recentWinRate
    val formB = b.recentWinRate
    if (formA != null && formB != null) score += (formA - formB) * 1.15
    val surfaceA = a.surfaceWinRate(surface)
    val surfaceB = b.surfaceWinRate(surface)
    if (surfaceA != null && surfaceB != null) score += (surfaceA - surfaceB) * 0.75
    if (h2h.matches.isNotEmpty()) {
        val total = h2h.playerAWins + h2h.playerBWins
        if (total > 0) score += ((h2h.playerAWins - h2h.playerBWins).toDouble() / total) * 0.35
    }
    val detailedA = a.tennisStatPower(surface)
    val detailedB = b.tennisStatPower(surface)
    if (detailedA != null && detailedB != null) {
        score += (detailedA - detailedB) * 0.55
    }
    score += ((a.rankingMove ?: 0) - (b.rankingMove ?: 0)).coerceIn(-20, 20) / 80.0
    score += (a.fatiguePenalty() - b.fatiguePenalty()) * -0.55
    return score
}

private fun tennisConfidence(a: TennisPlayerSnapshot, b: TennisPlayerSnapshot, h2h: TennisH2hSummary): Int {
    var score = 34
    if (a.rank != null) score += 10
    if (b.rank != null) score += 10
    score += (a.recentCompleted.size.coerceAtMost(12) + b.recentCompleted.size.coerceAtMost(12))
    if (a.recentCompleted.any { it.surface != "Surface inconnue" }) score += 5
    if (b.recentCompleted.any { it.surface != "Surface inconnue" }) score += 5
    if (a.detailedMatches.size >= 5) score += 7 else score += a.detailedMatches.size
    if (b.detailedMatches.size >= 5) score += 7 else score += b.detailedMatches.size
    if (h2h.matches.isNotEmpty()) score += 6
    return score.coerceIn(30, 90)
}

private fun tennisSourceAgreement(a: TennisPlayerSnapshot, b: TennisPlayerSnapshot, h2h: TennisH2hSummary): Int =
    (48 + listOf(a, b).sumOf { player ->
            (if (player.rank != null) 8 else 0) +
            (if (player.recentCompleted.size >= 5) 8 else player.recentCompleted.size) +
            (if (player.careerWins != null) 4 else 0) +
            (if (player.detailedMatches.size >= 5) 8 else player.detailedMatches.size)
    } + if (h2h.matches.isNotEmpty()) 6 else 0).coerceIn(40, 92)

private fun TennisPlayerSnapshot.summaryLines(surface: String): List<String> = buildList {
    val ranking = rank?.let { rankValue ->
        val move = rankingMove?.let { moveValue ->
            when {
                moveValue > 0 -> "hausse +$moveValue places"
                moveValue < 0 -> "baisse ${abs(moveValue)} places"
                else -> "stable"
            }
        }
        "$name : classement $tour #$rankValue${rankingPoints?.let { ", $it pts" }.orEmpty()}${move?.let { " ($it)" }.orEmpty()}"
    }
    ranking?.let(::add) ?: add("$name : classement ATP/WTA non trouvé")
    careerWins?.let { wins ->
        val losses = careerLosses ?: 0
        val label = if (detailedMatches.isNotEmpty()) "bilan 365j" else "bilan carrière ESPN"
        add("$name : $label ${wins}V-${losses}D${titles?.let { ", $it titres" }.orEmpty()}")
    }
    detailedStats()?.let { stats ->
        add("$name : service 365j ${stats.shortServeText()}")
        add("$name : retour/pression 365j ${stats.shortReturnText()}")
    }
    detailedStats(surface)?.let { stats ->
        add("$name : sur $surface ${stats.wins}V-${stats.losses}D${stats.acesPerMatch?.let { ", ${formatOne(it)} aces/match" }.orEmpty()}")
    }
    if (recentCompleted.isNotEmpty()) {
        add("$name : forme récente ${recentWins}V-${recentLosses}D sur ${recentCompleted.size} matchs")
        trendLabel()?.let { add("$name : dynamique $it") }
        val recentLoad = recentLoadText()
        if (recentLoad.isNotBlank()) add("$name : $recentLoad")
        val last = recentCompleted.first()
        add("$name : dernier match ${if (last.won == true) "victoire" else "défaite"} vs ${last.opponent} (${last.tournament}, ${last.surface})")
    } else {
        add("$name : aucun historique match récent trouvé")
    }
}.distinct()

private fun TennisH2hSummary.summaryLines(aName: String, bName: String): List<String> = buildList {
    if (matches.isEmpty()) {
        add("$aName vs $bName : aucun face-à-face trouvé sur les matchs publics consultés")
    } else {
        add("$aName vs $bName : face-à-face ${playerAWins}-${playerBWins} sur ${matches.size} match(s)")
        bySurface.forEach { (surface, record) ->
            add("$aName vs $bName : H2H $surface ${record.first}-${record.second}")
        }
        matches.firstOrNull()?.let { last ->
            add("$aName vs $bName : dernier H2H ${last.tournament}, ${last.surface}")
        }
    }
}

private fun tennisScenarios(
    a: TennisPlayerSnapshot,
    b: TennisPlayerSnapshot,
    h2h: TennisH2hSummary,
    surface: String,
    bestOf: Int,
    selection: String,
    probability: Double,
): List<Triple<String, String, Double>> = buildList {
    add(Triple("Résultat tennis", "Victoire $selection", probability))
    val close = (1.0 - abs(probability - 0.5) * 2.0).coerceIn(0.10, 0.92)
    val expected = expectedTennisScore(selection, probability, bestOf)
    add(Triple("Score sets", expected, probability.coerceAtMost(0.74)))
    add(Triple("Total sets", if (bestOf == 5) "Plus de 3,5 sets" else "Plus de 2,5 sets", (close * 0.72).coerceIn(0.34, 0.72)))
    strongestSurfaceSide(a, b, surface)?.let { (name, chance) ->
        add(Triple("Surface", "$name avantage $surface", chance))
    }
    if (h2h.matches.isNotEmpty()) {
        val leader = if (h2h.playerAWins >= h2h.playerBWins) a.name else b.name
        val lead = abs(h2h.playerAWins - h2h.playerBWins)
        add(Triple("Face-à-face", "$leader avantage H2H +$lead", (0.50 + lead * 0.08).coerceIn(0.35, 0.75)))
    }
    aceScenario(a, b, surface)?.let { add(it) }
    breakScenario(a, b)?.let { add(it) }
}

private fun tennisPlayerScenarios(
    a: TennisPlayerSnapshot,
    b: TennisPlayerSnapshot,
): List<Triple<String, String, Double>> = listOf(a, b).flatMap { player ->
    val stats = player.detailedStats()
    buildList {
        stats?.acesPerMatch?.let { aces ->
            val line = floor(aces.coerceAtLeast(1.0)).roundToInt().coerceAtLeast(1)
            add(Triple("Aces tennis", "${player.name} plus de $line aces", (0.46 + (aces - line) * 0.12).coerceIn(0.42, 0.72)))
        }
        stats?.holdPct?.let { hold ->
            add(Triple("Service tennis", "${player.name} conserve son service souvent", hold.coerceIn(0.38, 0.82)))
        }
        stats?.breakPointsConvertedPct?.let { converted ->
            add(Triple("Break tennis", "${player.name} convertit une balle de break", converted.coerceIn(0.28, 0.70)))
        }
    }
}

private fun strongestSurfaceSide(
    a: TennisPlayerSnapshot,
    b: TennisPlayerSnapshot,
    surface: String,
): Pair<String, Double>? {
    val aRate = a.surfaceWinRate(surface) ?: return null
    val bRate = b.surfaceWinRate(surface) ?: return null
    if (abs(aRate - bRate) < 0.10) return null
    val leader = if (aRate >= bRate) a.name else b.name
    val diff = abs(aRate - bRate)
    return leader to (0.52 + diff * 0.28).coerceIn(0.52, 0.72)
}

private fun aceScenario(
    a: TennisPlayerSnapshot,
    b: TennisPlayerSnapshot,
    surface: String,
): Triple<String, String, Double>? {
    val candidates = listOf(a, b).mapNotNull { player ->
        val stats = player.detailedStats(surface) ?: player.detailedStats()
        val aces = stats?.acesPerMatch ?: return@mapNotNull null
        player to aces
    }
    val best = candidates.maxByOrNull { it.second } ?: return null
    if (best.second < 2.2) return null
    val line = floor(best.second).roundToInt().coerceAtLeast(1)
    return Triple("Aces tennis", "${best.first.name} plus de $line aces", (0.48 + (best.second - line) * 0.10).coerceIn(0.45, 0.70))
}

private fun breakScenario(a: TennisPlayerSnapshot, b: TennisPlayerSnapshot): Triple<String, String, Double>? {
    val values = listOf(a, b).mapNotNull { player ->
        val stats = player.detailedStats()
        val converted = stats?.breakPointsConvertedPct ?: return@mapNotNull null
        player to converted
    }
    val best = values.maxByOrNull { it.second } ?: return null
    return Triple("Break tennis", "${best.first.name} obtient au moins un break", (0.46 + best.second * 0.28).coerceIn(0.45, 0.68))
}

private fun expectedTennisScore(selection: String, probability: Double, bestOf: Int): String = when {
    bestOf == 5 && probability >= 0.68 -> "$selection 3-0 / 3-1"
    bestOf == 5 -> "$selection 3-1 / 3-2"
    probability >= 0.68 -> "$selection 2-0"
    else -> "$selection 2-1"
}

private fun TennisPlayerSnapshot.surfaceStats(surface: String): Pair<Int, Int> {
    val matches = recentCompleted.filter { it.surface == surface }
    return matches.count { it.won == true } to matches.count { it.won == false }
}

private fun TennisPlayerSnapshot.surfaceWinRate(surface: String): Double? {
    val stats = surfaceStats(surface)
    val total = stats.first + stats.second
    return if (total > 0) stats.first.toDouble() / total else null
}

private fun TennisPlayerSnapshot.tennisStatPower(surface: String): Double? {
    val stats = detailedStats(surface) ?: detailedStats() ?: return null
    var score = 0.0
    stats.holdPct?.let { score += (it - 0.72) * 1.2 }
    stats.firstServeWonPct?.let { score += (it - 0.68) * 0.8 }
    stats.secondServeWonPct?.let { score += (it - 0.50) * 0.7 }
    stats.breakPointsConvertedPct?.let { score += (it - 0.38) * 0.7 }
    stats.breakPointsSavedPct?.let { score += (it - 0.60) * 0.5 }
    stats.acesPerMatch?.let { score += (it - 4.0).coerceIn(-4.0, 8.0) / 40.0 }
    stats.doubleFaultsPerMatch?.let { score -= (it - 2.5).coerceIn(-2.0, 6.0) / 35.0 }
    return score
}

private fun TennisDetailedStats.shortServeText(): String = listOfNotNull(
    acesPerMatch?.let { "${formatOne(it)} aces/match" },
    doubleFaultsPerMatch?.let { "${formatOne(it)} doubles fautes/match" },
    firstServeInPct?.let { "${formatPct(it)} 1res balles" },
    firstServeWonPct?.let { "${formatPct(it)} pts gagnés sur 1re" },
    secondServeWonPct?.let { "${formatPct(it)} pts gagnés sur 2e" },
    holdPct?.let { "${formatPct(it)} jeux de service tenus" },
).joinToString(", ").ifBlank { "aucune stat service exploitable" }

private fun TennisDetailedStats.shortReturnText(): String = listOfNotNull(
    breakPointsCreatedPerMatch?.let { "${formatOne(it)} balles de break créées/match" },
    breakPointsConvertedPct?.let { "${formatPct(it)} break points convertis" },
    breakPointsSavedPct?.let { "${formatPct(it)} break points sauvés" },
    minutesAvg?.let { "match moyen ${it.roundToInt()} min" },
).joinToString(", ").ifBlank { "aucune stat retour exploitable" }

private fun formatPct(value: Double): String = "${(value * 100).roundToInt()}%"

private fun formatOne(value: Double): String = String.format(Locale.FRANCE, "%.1f", value)

private fun TennisPlayerSnapshot.trendLabel(): String? {
    val completed = recentCompleted
    if (completed.size < 6) return null
    val last = completed.take(5).map { if (it.won == true) 1.0 else 0.0 }.average()
    val before = completed.drop(5).take(5).map { if (it.won == true) 1.0 else 0.0 }.takeIf { it.isNotEmpty() }?.average() ?: return null
    return when {
        last - before >= 0.25 -> "en hausse"
        before - last >= 0.25 -> "en baisse"
        else -> "stable"
    }
}

private fun TennisPlayerSnapshot.recentLoadText(): String {
    val now = System.currentTimeMillis()
    val recent = recentCompleted.count { now - it.date in 0..(14L * 24 * 60 * 60 * 1000) }
    return when {
        recent >= 5 -> "charge élevée : $recent matchs sur 14 jours"
        recent >= 3 -> "charge notable : $recent matchs sur 14 jours"
        recent > 0 -> "charge légère : $recent match(s) sur 14 jours"
        else -> ""
    }
}

private fun TennisPlayerSnapshot.fatiguePenalty(): Double {
    val now = System.currentTimeMillis()
    val recent = recentCompleted.count { now - it.date in 0..(10L * 24 * 60 * 60 * 1000) }
    return (recent / 8.0).coerceIn(0.0, 0.75)
}

private fun TennisPlayerSnapshot.serveHoldProxy(surface: String): Double {
    val base = when (surface) {
        "Gazon" -> 0.68
        "Dur indoor" -> 0.65
        "Dur" -> 0.62
        "Terre battue" -> 0.57
        else -> 0.60
    }
    val rankingBoost = rank?.let { ((120 - it).coerceIn(-80, 100)) / 500.0 } ?: 0.0
    val formBoost = recentWinRate?.let { (it - 0.5) * 0.12 } ?: 0.0
    return (base + rankingBoost + formBoost).coerceIn(0.35, 0.82)
}

private fun inferSurface(tournament: String, venue: String, court: String, indoor: Boolean): String {
    if (indoor) return "Dur indoor"
    val text = normalizeName("$tournament $venue $court")
    return when {
        listOf("wimbledon", "halle", "queens", "stuttgart", "eastbourne", "nottingham", "s hertogenbosch", "libema", "bad homburg", "mallorca").any { it in text } -> "Gazon"
        listOf("roland garros", "french open", "rome", "madrid", "monte carlo", "barcelona", "hamburg", "geneva", "munich", "estoril", "bastad", "gstaad", "kitzbuhel", "clay").any { it in text } -> "Terre battue"
        listOf("paris masters", "basel", "vienna", "rotterdam", "stockholm", "antwerp", "metz", "indoor").any { it in text } -> "Dur indoor"
        listOf("australian open", "us open", "indian wells", "miami", "cincinnati", "canada", "toronto", "montreal", "shanghai", "beijing", "tokyo", "dubai", "doha", "brisbane", "adelaide", "hard").any { it in text } -> "Dur"
        else -> "Surface inconnue"
    }
}

private fun tournamentNameFromId(id: String): String = when (id) {
    "188" -> "Wimbledon"
    "172" -> "Roland Garros"
    "154" -> "Australian Open"
    "560" -> "US Open"
    "414" -> "Rome"
    "413" -> "Madrid"
    "404" -> "Indian Wells"
    "403" -> "Miami"
    else -> ""
}

private fun logistic(value: Double): Double = 1.0 / (1.0 + exp(-value))

private fun String.httpsRef(): String = replace("http://", "https://")

private fun JsonElement.objOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.obj(name: String): JsonObject? = get(name)?.objOrNull()
private fun JsonObject.array(name: String): List<JsonElement> = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
private fun JsonObject.text(name: String): String? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()
private fun JsonObject.number(name: String): Double? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asDouble }.getOrNull()
private fun JsonObject.int(name: String): Int? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asInt }.getOrNull()
private fun JsonObject.bool(name: String): Boolean? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asBoolean }.getOrNull()

private fun normalizeName(value: String): String {
    val normalized = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    return normalized.replace(Regex("[^a-z0-9]+"), " ").trim()
}

private fun playerTokens(value: String): List<String> = normalizeName(value).split(' ').filter { it.isNotBlank() }
