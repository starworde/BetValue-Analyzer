package com.soliano.betvalueanalyzer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TennisAnalysisProviderTest {
    @Test
    fun `tennis deep prediction keeps both players stats scenarios and recent info separated`() {
        val now = 1_786_000_000_000L
        val playerA = player(
            name = "Rafael Jodar",
            rank = 26,
            previousRank = 28,
            points = 1839,
            wins = 26,
            losses = 11,
            aces = 6,
            surface = "Gazon",
            now = now,
        )
        val playerB = player(
            name = "Felix Gill",
            rank = 173,
            previousRank = 168,
            points = 355,
            wins = 18,
            losses = 15,
            aces = 3,
            surface = "Gazon",
            now = now,
        )
        val snapshot = TennisAnalysisSnapshot(
            target = DeepAnalysisTarget(
                id = "tennis-wimbledon-jodar-gill",
                sportKey = "tennis/atp",
                sportTitle = "Tennis",
                competitionKey = "tennis:atp:wimbledon",
                competitionName = "ATP · Wimbledon · Men's Singles",
                commenceTime = now,
                homeTeam = playerA.name,
                awayTeam = playerB.name,
            ),
            playerA = playerA,
            playerB = playerB,
            h2h = TennisH2hSummary(
                matches = listOf(
                    TennisRecentMatch(
                        id = "h2h-1",
                        date = now - days(120),
                        tournament = "Challenger",
                        round = "R16",
                        opponent = playerB.name,
                        opponentId = null,
                        won = true,
                        surface = "Gazon",
                        note = "2-1",
                    )
                ),
                playerAWins = 1,
                playerBWins = 0,
                bySurface = mapOf("Gazon" to (1 to 0)),
            ),
            surface = "Gazon",
            bestOf = 5,
            sources = listOf("ESPN rankings ATP/WTA", "TennisMyLife ATP match stats CSV"),
        )

        val prediction = snapshot.toPredictionEntity(updateTime = now)
        val summary = prediction.statSummary
        val playerScenarios = prediction.playerScenarios
        val recentInfoScopes = prediction.contextInsights.lines()
            .map { it.substringBefore(':').trim() }

        assertTrue(summary.contains("Rafael Jodar : classement ATP #26"))
        assertTrue(summary.contains("Felix Gill : classement ATP #173"))
        assertTrue(summary.contains("Rafael Jodar : service 365j"))
        assertTrue(summary.contains("Felix Gill : service 365j"))
        assertTrue(summary.contains("Rafael Jodar : retour/pression 365j"))
        assertTrue(summary.contains("Felix Gill : retour/pression 365j"))
        assertTrue(summary.contains("Rafael Jodar vs Felix Gill : face-à-face 1-0"))

        assertTrue(playerScenarios.contains("Rafael Jodar plus de"))
        assertTrue(playerScenarios.contains("Felix Gill plus de"))
        assertFalse(playerScenarios.lines().any { it.contains("Jodar plus de 5 Rafael", ignoreCase = true) })

        assertEquals(listOf("Rafael Jodar", "Felix Gill"), recentInfoScopes)
        assertTrue(prediction.contextInsights.lines().all { it.endsWith("Aucun fait relevé") })
    }

    private fun player(
        name: String,
        rank: Int,
        previousRank: Int,
        points: Int,
        wins: Int,
        losses: Int,
        aces: Int,
        surface: String,
        now: Long,
    ): TennisPlayerSnapshot =
        TennisPlayerSnapshot(
            name = name,
            tour = "ATP",
            espnId = null,
            rank = rank,
            previousRank = previousRank,
            rankingPoints = points,
            rankingMove = previousRank - rank,
            country = "ESP",
            age = 20,
            careerWins = wins,
            careerLosses = losses,
            titles = 1,
            recentMatches = List(6) { index ->
                TennisRecentMatch(
                    id = "$name-recent-$index",
                    date = now - days(index + 5L),
                    tournament = "Tournoi $index",
                    round = "Tour",
                    opponent = "Adversaire $index",
                    opponentId = null,
                    won = index < 4,
                    surface = surface,
                    note = "2-0",
                )
            },
            detailedMatches = List(8) { index ->
                TennisDetailedMatch(
                    id = "$name-detailed-$index",
                    date = now - days(index + 10L),
                    tournament = "Tournoi détaillé $index",
                    round = "Tour",
                    opponent = "Adversaire stats $index",
                    won = index < 5,
                    surface = surface,
                    score = "2-0",
                    bestOf = 3,
                    minutes = 95 + index,
                    playerRank = rank,
                    playerRankPoints = points,
                    playerCountry = "ESP",
                    playerAge = 20.0,
                    playerHand = "R",
                    opponentRank = rank + 20,
                    opponentRankPoints = points - 100,
                    aces = aces,
                    doubleFaults = 2,
                    servicePoints = 72,
                    firstServeIn = 45,
                    firstServeWon = 34,
                    secondServeWon = 15,
                    serviceGames = 10,
                    breakPointsSaved = 4,
                    breakPointsFaced = 6,
                    opponentBreakPointsSaved = 3,
                    opponentBreakPointsFaced = 7,
                )
            },
        )

    private fun days(value: Long): Long = value * 24 * 60 * 60 * 1000
}
