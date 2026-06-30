package com.soliano.betvalueanalyzer.ui.screens

import com.soliano.betvalueanalyzer.data.UserSettings
import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.data.local.UpcomingEventEntity
import com.soliano.betvalueanalyzer.ui.AppUiState
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScreenDiagnosticsTest {
    @Test
    fun `source health exposes github action and firestore diagnostic`() {
        val state = AppUiState(
            settings = UserSettings(
                cloudCollaborativeEnabled = true,
                lastSyncEpoch = 1_800_000_000_000L,
                lastCloudReadEpoch = 1_800_000_000_000L,
                lastCloudUploadEpoch = 1_800_000_000_000L,
                cloudJobStatus = "success",
                cloudJobFinishedEpoch = 1_800_000_000_000L,
                cloudJobEventsFound = 2_148,
                cloudJobResultsPrepared = 900,
                cloudJobResultsWritten = 860,
                cloudJobRemovedSportsDeleted = 12,
                cloudJobSourcesChecked = 18,
            ),
        )

        val rows = sourceHealthRows(state, "fr")
        val github = rows.single { it.label == "GitHub Actions" }.value
        val firestoreCleanup = rows.single { it.label == "Firestore quota/nettoyage" }.value

        assertTrue(github.contains("success", ignoreCase = true))
        assertTrue(github.contains("860/900"))
        assertTrue(github.contains("2148") || github.contains("2 148"))
        assertTrue(firestoreCleanup.contains("OK"))
    }

    @Test
    fun `sport diagnostics expose source update counts stats and confidence`() {
        val state = AppUiState(
            upcomingEvents = listOf(
                upcoming(
                    id = "volley-1",
                    sportKey = "volleyball/vnl",
                    sportTitle = "Volley-ball",
                    competitionName = "Volleyball Nations League",
                    sourceName = "Volleyball World officiel",
                ),
                upcoming(
                    id = "hand-1",
                    sportKey = "handball/ehf",
                    sportTitle = "Handball",
                    competitionName = "EHF Champions League",
                    sourceName = "TheSportsDB",
                ),
            ),
            predictions = listOf(
                prediction(
                    id = "volley-p",
                    sportKey = "volleyball/vnl",
                    sportTitle = "Volley-ball",
                    sourceName = "volley multi-source",
                    statSummary = "sets gagnés/perdus, points par set, réception, contres, score en sets",
                    scenarios = "Vainqueur volley · Total points match · Handicap sets",
                    confidenceScore = 72,
                ),
                prediction(
                    id = "hand-p",
                    sportKey = "handball/ehf",
                    sportTitle = "Handball",
                    sourceName = "handball multi-source",
                    statSummary = "buts par match, écart moyen, gardien, exclusions 2 minutes, jets de 7 mètres",
                    scenarios = "Vainqueur temps réglementaire · Total buts · Handicap buts",
                    confidenceScore = 68,
                ),
            ),
            settings = UserSettings(lastSyncEpoch = 1_800_000_000_000L),
        )

        val rows = sourceHealthSportDiagnosticRows(state, "fr")
        val volley = rows.single { it.label == "Volley-ball" }.value
        val handball = rows.single { it.label == "Handball" }.value

        listOf("1 match", "source", "MAJ", "stats dispo", "stats manquantes", "confiance").forEach { token ->
            assertTrue("diagnostic volley incomplet: $volley", volley.contains(token))
            assertTrue("diagnostic handball incomplet: $handball", handball.contains(token))
        }
        assertTrue(volley.contains("points par set"))
        assertTrue(volley.contains("contres"))
        assertTrue(handball.contains("buts"))
        assertTrue(handball.contains("gardien"))
    }

    private fun upcoming(
        id: String,
        sportKey: String,
        sportTitle: String,
        competitionName: String,
        sourceName: String,
    ) = UpcomingEventEntity(
        id = id,
        sportKey = sportKey,
        sportTitle = sportTitle,
        competitionKey = "$sportKey:$competitionName",
        competitionName = competitionName,
        commenceTime = 1_800_000_100_000L,
        eventName = "$competitionName event",
        participantA = "A",
        participantB = "B",
        eventType = "MATCH",
        sourceName = sourceName,
        analysisId = null,
    )

    private fun prediction(
        id: String,
        sportKey: String,
        sportTitle: String,
        sourceName: String,
        statSummary: String,
        scenarios: String,
        confidenceScore: Int,
    ) = PredictionEntity(
        id = id,
        eventId = "$id-event",
        sportKey = sportKey,
        sportTitle = sportTitle,
        competitionName = "Compétition test",
        commenceTime = 1_800_000_100_000L,
        homeTeam = "A",
        awayTeam = "B",
        market = "Marché",
        selection = "A",
        betclicOdds = 1.0,
        impliedProbability = 0.6,
        consensusProbability = 0.6,
        valueEdge = 0.0,
        expectedValue = 0.0,
        confidenceScore = confidenceScore,
        riskLevel = "Modéré",
        category = "Mitigé",
        bookmakerCount = 0,
        sourceName = sourceName,
        sourceLastUpdate = 1_800_000_000_000L,
        explanation = "",
        positiveArguments = "",
        negativeArguments = "",
        expectedScore = "",
        statSummary = statSummary,
        scenarios = scenarios,
        homeLineupStatus = "",
        homeLineup = "",
        awayLineupStatus = "",
        awayLineup = "",
        playerScenarios = "",
        sourceDetails = "",
        contextInsights = "",
        sourceAgreement = confidenceScore,
    )
}
