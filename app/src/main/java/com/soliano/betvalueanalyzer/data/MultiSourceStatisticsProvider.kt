package com.soliano.betvalueanalyzer.data

import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import com.soliano.betvalueanalyzer.domain.PlayerStatProfile
import com.soliano.betvalueanalyzer.domain.TeamProfileRequest
import com.soliano.betvalueanalyzer.domain.TeamStatProfile
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class MultiSourceStatisticsProvider(api: PublicSportsApiService, deepAnalysis: Boolean = false) {
    private val espn = TeamStatisticsProvider(api)
    private val fotMob = FotMobStatisticsProvider(api)
    private val fifa = FifaStatisticsProvider(
        api = api,
        historyDays = if (deepAnalysis) 365 else 300,
        maxDetailMatches = if (deepAnalysis) 24 else 6,
    )
    private val theSportsDb = TheSportsDbStatisticsProvider(api)
    private val openLiga = OpenLigaStatisticsProvider(api)
    private val rugby = RugbyStatisticsProvider(
        api = api,
        historyDays = if (deepAnalysis) 365 else 240,
        maxMatches = if (deepAnalysis) 18 else 8,
    )

    suspend fun load(requests: List<TeamProfileRequest>): Map<String, TeamStatProfile> = coroutineScope {
        val espnDeferred = async { espn.load(requests) }
        val fotMobDeferred = async { fotMob.load(requests) }
        val fifaDeferred = async { fifa.load(requests) }
        val theSportsDbDeferred = async { theSportsDb.load(requests) }
        val openLigaDeferred = async { openLiga.load(requests) }
        val rugbyDeferred = async { rugby.load(requests) }
        val espnProfiles = espnDeferred.await()
        val fotMobProfiles = fotMobDeferred.await()
        val fifaProfiles = fifaDeferred.await()
        val theSportsDbProfiles = theSportsDbDeferred.await()
        val openLigaProfiles = openLigaDeferred.await()
        val rugbyProfiles = rugbyDeferred.await()
        (espnProfiles.keys + fotMobProfiles.keys + fifaProfiles.keys + theSportsDbProfiles.keys + openLigaProfiles.keys + rugbyProfiles.keys).associateWith { key ->
            mergeAll(listOfNotNull(
                espnProfiles[key],
                fotMobProfiles[key],
                fifaProfiles[key],
                theSportsDbProfiles[key],
                openLigaProfiles[key],
                rugbyProfiles[key],
            ))
        }
    }

    private fun mergeAll(profiles: List<TeamStatProfile>): TeamStatProfile {
        if (profiles.size == 1) return profiles.single()
        val primary = profiles.first()
        val games = minOf(6, profiles.maxOf { it.games }).coerceAtLeast(1)
        val weights = profiles.associateWith { kotlin.math.sqrt(it.games.coerceIn(1, 6).toDouble()) }
        val totalWeight = weights.values.sum().coerceAtLeast(1.0)
        val winRate = profiles.sumOf { it.winRate * weights.getValue(it) } / totalWeight
        val drawRate = profiles.sumOf { it.drawRate * weights.getValue(it) } / totalWeight
        val wins = (winRate * games).roundToInt().coerceIn(0, games)
        val draws = (drawRate * games).roundToInt().coerceIn(0, games - wins)
        val averageScored = profiles.sumOf { it.averageScored * weights.getValue(it) } / totalWeight
        val averageConceded = profiles.sumOf { it.averageConceded * weights.getValue(it) } / totalWeight
        val agreement = (100.0 - profiles.map {
            abs(it.winRate - winRate) * 45.0 + abs(it.averageScored - averageScored) * 12.0 +
                abs(it.averageConceded - averageConceded) * 12.0
        }.average()).roundToInt().coerceIn(40, 96)
        val mergedPlayers = profiles.drop(1).fold(primary.playerProfiles) { current, profile ->
            mergePlayers(current, profile.playerProfiles)
        }
        return TeamStatProfile(
            teamName = primary.teamName,
            games = games,
            wins = wins,
            draws = draws,
            losses = games - wins - draws,
            averageScored = averageScored,
            averageConceded = averageConceded,
            averageShots = profiles.firstNotNullOfOrNull { it.averageShots },
            averageShotsOnTarget = profiles.firstNotNullOfOrNull { it.averageShotsOnTarget },
            averageCorners = profiles.firstNotNullOfOrNull { it.averageCorners },
            averagePossession = profiles.firstNotNullOfOrNull { it.averagePossession },
            recentLineup = profiles.firstNotNullOfOrNull { it.recentLineup },
            playerProfiles = mergedPlayers,
            sourceNames = profiles.flatMap { it.sourceNames }.distinct(),
            sourceSnapshots = profiles.flatMap { it.sourceSnapshots },
            sourceAgreement = agreement,
            coachName = profiles.firstNotNullOfOrNull { it.coachName },
            formTrend = profiles.sumOf { it.formTrend * weights.getValue(it) } / totalWeight,
        )
    }

    private fun mergePlayers(primary: List<PlayerStatProfile>, secondary: List<PlayerStatProfile>): List<PlayerStatProfile> {
        val merged = primary.map { recent ->
            val season = matchingPlayer(recent, secondary) ?: return@map recent
            val existingSecondarySources = (recent.sourceCount - 1).coerceAtLeast(0)
            val incomingSources = season.sourceCount.coerceAtLeast(1)
            val incomingGoalsSix = season.goals / season.appearances.coerceAtLeast(1) * 6.0
            val incomingAssistsSix = season.assists / season.appearances.coerceAtLeast(1) * 6.0
            val secondarySourceTotal = existingSecondarySources + incomingSources
            val combinedSourceCount = recent.sourceCount + incomingSources
            recent.copy(
                sourceCount = combinedSourceCount,
                secondaryGoals = if (existingSecondarySources == 0) incomingGoalsSix else {
                    (recent.secondaryGoals * existingSecondarySources + incomingGoalsSix * incomingSources) / secondarySourceTotal
                },
                secondaryAssists = if (existingSecondarySources == 0) incomingAssistsSix else {
                    (recent.secondaryAssists * existingSecondarySources + incomingAssistsSix * incomingSources) / secondarySourceTotal
                },
                availabilityNote = season.availabilityNote ?: recent.availabilityNote,
                goalTrend = (recent.goalTrend * recent.sourceCount + season.goalTrend * incomingSources) / combinedSourceCount,
                assistTrend = (recent.assistTrend * recent.sourceCount + season.assistTrend * incomingSources) / combinedSourceCount,
                formTrend = (recent.formTrend * recent.sourceCount + season.formTrend * incomingSources) / combinedSourceCount,
                totalMinutes = if (recent.totalMinutes > 0.0) recent.totalMinutes else season.totalMinutes,
                lastMatchMinutes = maxOf(recent.lastMatchMinutes, season.lastMatchMinutes),
                averageMinutes = listOf(recent.averageMinutes, season.averageMinutes).filter { it > 0.0 }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
                heavyRecentLoadCount = maxOf(recent.heavyRecentLoadCount, season.heavyRecentLoadCount),
            )
        }
        val known = primary.map { normalize(it.name) }.toSet()
        return merged + secondary.filter {
            normalize(it.name) !in known && (it.goals > 0 || it.assists > 0 || it.availabilityNote != null)
        }
    }

    private fun matchingPlayer(player: PlayerStatProfile, candidates: List<PlayerStatProfile>): PlayerStatProfile? {
        candidates.firstOrNull { normalize(it.name) == normalize(player.name) }?.let { return it }
        val targetTokens = playerTokens(player.name)
        if (targetTokens.isEmpty()) return null
        val sameLastName = candidates.filter { candidate ->
            val tokens = playerTokens(candidate.name)
            tokens.isNotEmpty() && tokens.last() == targetTokens.last() &&
                (tokens.first().firstOrNull() == targetTokens.first().firstOrNull())
        }
        return sameLastName.singleOrNull()
    }

    private fun playerTokens(value: String): List<String> = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotBlank() }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9à-ÿ]+"), "")
}
