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
                cloudJobConfiguredSports = setOf("soccer", "volleyball", "tennis"),
                cloudJobSportsWithoutEvents = setOf("boxing"),
                cloudJobEventsBySportSummary = "soccer:120, volleyball:24, tennis:80",
                cloudJobEventsFound = 2_148,
                cloudJobResultsPrepared = 900,
                cloudJobResultsWritten = 860,
                cloudJobRemovedSportsDeleted = 12,
                cloudJobSourcesChecked = 18,
                cloudJobSourceErrors = 1,
                cloudJobSourceErrorDetails = "UCI WorldTour : HTTP 503",
                aiFreeEnabled = setOf("Gemini free tier", "Groq free tier"),
                aiPaidDisabled = setOf("OpenAI", "Claude / Anthropic"),
                aiMode = "double",
                aiCalled = 14,
                aiResponded = 12,
                aiCacheHits = 9,
                aiFusionCount = 6,
                aiFallbackUsed = 2,
            ),
        )

        val rows = sourceHealthRows(state, "fr")
        val github = rows.single { it.label == "GitHub Actions" }.value
        val firestoreCleanup = rows.single { it.label == "Firestore quota/nettoyage" }.value
        val responding = rows.single { it.label == "Sources cloud qui répondent" }.value
        val empty = rows.single { it.label == "Sources cloud vides" }.value
        val errors = rows.single { it.label == "Sources cloud en erreur" }.value
        val aiFree = rows.single { it.label == "IA cloud active" }.value
        val aiFusion = rows.single { it.label == "Fusion IA" }.value
        val paidDisabled = rows.single { it.label == "IA payantes" }.value

        assertTrue(github.contains("success", ignoreCase = true))
        assertTrue(github.contains("860/900"))
        assertTrue(github.contains("2148") || github.contains("2 148"))
        assertTrue(firestoreCleanup.contains("OK"))
        assertTrue(responding.contains("volleyball"))
        assertTrue(empty.contains("boxing"))
        assertTrue(errors.contains("UCI WorldTour"))
        assertTrue(aiFree.contains("Gemini"))
        assertTrue(aiFree.contains("Groq"))
        assertTrue(aiFusion.contains("12/14"))
        assertTrue(aiFusion.contains("6 fusion"))
        assertTrue(aiFusion.contains("9 cache"))
        assertTrue(paidDisabled.contains("OpenAI"))
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
        referenceOdds = 1.0,
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
