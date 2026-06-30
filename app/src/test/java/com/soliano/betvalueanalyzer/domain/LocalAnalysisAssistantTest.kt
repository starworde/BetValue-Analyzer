package com.soliano.betvalueanalyzer.domain

import com.soliano.betvalueanalyzer.data.local.LiveEventEntity
import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAnalysisAssistantTest {
    @Test
    fun weakPredictionStaysInWatchModeWithoutInventingSignals() {
        val reading = LocalAnalysisAssistant.explain(
            prediction(
                sportKey = "soccer/fifa",
                sportTitle = "Football",
                selection = "Attendre données",
                category = "Données à compléter",
                confidenceScore = 30,
                sourceAgreement = 30,
                statSummary = "",
                contextInsights = "",
                scenarios = "",
                sourceDetails = "",
            )
        )

        assertTrue(reading.status == LocalAiStatus.WatchOnly || reading.status == LocalAiStatus.Weak || reading.status == LocalAiStatus.Impossible)
        assertTrue(reading.summary.contains("insuffisantes", ignoreCase = true))
        assertEquals(listOf("Aucun fait relevé"), reading.importantSignals)
        assertFalse(reading.importantSignals.joinToString(" ").contains("blessure", ignoreCase = true))
    }

    @Test
    fun footballReadingKeepsFootballVocabularyAndConfirmedPlayerNews() {
        val reading = LocalAnalysisAssistant.explain(
            prediction(
                sportKey = "soccer/world",
                sportTitle = "Football",
                homeTeam = "France",
                awayTeam = "Pays-Bas",
                selection = "France gagne",
                confidenceScore = 82,
                sourceAgreement = 86,
                expectedScore = "2 — 1",
                statSummary = """
                    France : 2,1 buts, 16 tirs, 6 tirs cadrés, corners élevés.
                    Pays-Bas : 1,1 buts, 10 tirs, cartons récents.
                """.trimIndent(),
                contextInsights = """
                    France : retour de blessure de Kylian Mbappe.
                    Pays-Bas : suspension après carton rouge au dernier match.
                    Composition probable confirmée par deux sources.
                """.trimIndent(),
                scenarios = "Score exact|2 — 1|0.61",
                sourceDetails = "FIFA officiel\nPresse équipe\nRéseaux officiels",
                homeLineupStatus = "Probable",
                homeLineup = "10|Kylian Mbappe|9",
                awayLineupStatus = "Probable",
                awayLineup = "4|Virgil van Dijk|4",
            )
        )

        val text = (reading.sportVocabulary + " " + reading.importantSignals.joinToString(" ")).lowercase()
        assertTrue(text.contains("football"))
        assertTrue(text.contains("buts"))
        assertTrue(text.contains("composition"))
        assertTrue(text.contains("retour"))
        assertTrue(text.contains("carton") || text.contains("suspension"))
        assertFalse(reading.status == LocalAiStatus.Impossible)
        val titles = reading.sections.map { it.title }
        assertTrue(titles.contains("Analyse IA approfondie"))
        assertTrue(titles.contains("Lecture globale du match/événement"))
        assertTrue(titles.contains("Analyse tactique / sportive"))
        assertTrue(titles.contains("Dernières nouvelles"))
        assertTrue(titles.contains("Pourquoi le favori peut perdre"))
        assertTrue(titles.contains("Pourquoi l’outsider peut gagner"))
        assertTrue(titles.contains("Conclusion argumentée"))
        assertTrue(titles.contains("Sources & transparence"))
        val analysis = reading.sections.first { it.title == "Analyse IA approfondie" }.lines.joinToString(" ")
        assertTrue(analysis.contains("Ce que ça change"))
        assertTrue(analysis.contains("Lecture terrain"))
        assertTrue(analysis.contains("Lecture opérationnelle"))
        val tactical = reading.sections.first { it.title == "Analyse tactique / sportive" }.lines.joinToString(" ")
        assertTrue(tactical.contains("Pressing"))
        assertTrue(tactical.contains("Compositions"))
        val conclusion = reading.sections.first { it.title == "Conclusion argumentée" }.lines.joinToString(" ")
        assertTrue(conclusion.contains("Scénario le plus probable"))
        assertTrue(conclusion.contains("Scénario alternatif crédible"))
    }

    @Test
    fun tennisReadingUsesTwoPlayersSurfaceServiceReturnAndNoFootballWords() {
        val reading = LocalAnalysisAssistant.explain(
            prediction(
                sportKey = "tennis/atp",
                sportTitle = "Tennis",
                homeTeam = "Rafael Jodar",
                awayTeam = "Felix Gill",
                market = "Vainqueur tennis",
                selection = "Rafael Jodar",
                confidenceScore = 68,
                sourceAgreement = 72,
                expectedScore = "Rafael Jodar 3-1 / 3-2",
                statSummary = """
                    Rafael Jodar : classement ATP #26, forme 365j 26V-11D, service 5,8 aces/match, 66% premières balles.
                    Felix Gill : classement ATP #94, forme 365j 19V-16D, retour 4,1 balles de break créées/match.
                    Surface gazon : Jodar 63% de victoires, Gill 49%.
                    Fatigue : Jodar dernier match 129 min, Gill dernier match 181 min.
                """.trimIndent(),
                contextInsights = """
                    Rafael Jodar : aucun fait relevé.
                    Felix Gill : retour de gêne musculaire à surveiller.
                """.trimIndent(),
                scenarios = "Face-à-face|Jodar avantage H2H +2|0.66",
                playerScenarios = "Aces tennis|Rafael Jodar plus de 5 aces|0.55\nBreaks|Felix Gill plus de 2 breaks créés|0.52",
                sourceDetails = "ATP\nITF\nWimbledon officiel",
            )
        )

        val vocabulary = reading.sportVocabulary.lowercase()
        assertTrue(vocabulary.contains("surface"))
        assertTrue(vocabulary.contains("service"))
        assertTrue(vocabulary.contains("retour"))
        assertTrue(vocabulary.contains("sets"))
        assertFalse(vocabulary.contains("buts"))
        assertTrue(reading.sections.any { it.title.contains("Rafael Jodar") })
        assertTrue(reading.sections.any { it.title.contains("Felix Gill") })
        val analysis = reading.sections.first { it.title == "Analyse IA approfondie" }.lines.joinToString(" ").lowercase()
        assertTrue(analysis.contains("surface"))
        assertTrue(analysis.contains("service"))
        assertTrue(analysis.contains("classement") || analysis.contains("fatigue"))
        val tactical = reading.sections.first { it.title == "Analyse tactique / sportive" }.lines.joinToString(" ").lowercase()
        assertTrue(tactical.contains("surface"))
        assertTrue(tactical.contains("service"))
        assertTrue(tactical.contains("retour"))
        assertTrue(tactical.contains("fatigue") || tactical.contains("h2h"))
    }

    @Test
    fun cyclingAndRacingAvoidFakeScoreVocabulary() {
        val cycling = LocalAnalysisAssistant.explain(
            prediction(
                sportKey = "cycling/uci",
                sportTitle = "Cyclisme",
                homeTeam = "Tour de France",
                awayTeam = "Peloton",
                selection = "Podium favori à surveiller",
                market = "Podium",
                confidenceScore = 61,
                sourceAgreement = 67,
                expectedScore = "Top 3 projeté",
                statSummary = "Parcours montagne, météo vent, startlist confirmée, favoris grimpeurs, rôle d’équipe clair.",
                contextInsights = "Startlist officielle publiée, aucune chute relevée.",
                scenarios = "Podium|Favori top 3|0.58",
                sourceDetails = "UCI\nOrganisateur\nPresse cyclisme",
            )
        )
        val racing = LocalAnalysisAssistant.explainLive(
            liveEvent(
                sportKey = "racing/f1",
                sportTitle = "Formule 1",
                statSummary = "Top 3 / classement : 1. Russell · 2. Verstappen · 3. Antonelli\nTours restants : tour 57\nÉcart : 1,4s",
                scenarios = "Podium|Russell conserve top 3|0.70",
                sourceDetails = "OpenF1\nF1 officiel",
            )
        )

        assertTrue(cycling.sportVocabulary.lowercase().contains("parcours"))
        assertTrue(cycling.sportVocabulary.lowercase().contains("startlist"))
        assertFalse(cycling.sportVocabulary.lowercase().contains("buts"))
        assertTrue(racing.sportVocabulary.lowercase().contains("classement"))
        assertTrue(racing.sportVocabulary.lowercase().contains("tours"))
        assertFalse(racing.summary.lowercase().contains("score final probable"))
        val liveAnalysis = racing.sections.first { it.title == "Analyse IA live" }.lines.joinToString(" ").lowercase()
        assertTrue(liveAnalysis.contains("état réel") || liveAnalysis.contains("etat reel"))
        assertTrue(liveAnalysis.contains("stratégie") || liveAnalysis.contains("tours"))
        assertTrue(racing.sections.any { it.title == "Conclusion live argumentée" })
        assertTrue(racing.sections.any { it.title == "Sources & transparence live" })
    }

    private fun prediction(
        id: String = "p1",
        eventId: String = "e1",
        sportKey: String = "soccer/fifa",
        sportTitle: String = "Football",
        competitionName: String = "Test Cup",
        commenceTime: Long = 1_790_000_000_000,
        homeTeam: String = "Equipe A",
        awayTeam: String = "Equipe B",
        market: String = "Résultat final",
        selection: String = "Equipe A",
        confidenceScore: Int = 70,
        category: String = "SAFE",
        sourceName: String = "Sources test",
        sourceLastUpdate: Long = 1_790_000_000_000,
        positiveArguments: String = "",
        negativeArguments: String = "",
        expectedScore: String = "1 — 0",
        statSummary: String = "Equipe A : forme solide.",
        scenarios: String = "Résultat|Equipe A|0.63",
        homeLineupStatus: String = "",
        homeLineup: String = "",
        awayLineupStatus: String = "",
        awayLineup: String = "",
        playerScenarios: String = "",
        sourceDetails: String = "Source A\nSource B",
        contextInsights: String = "",
        sourceAgreement: Int = 70,
    ) = PredictionEntity(
        id = id,
        eventId = eventId,
        sportKey = sportKey,
        sportTitle = sportTitle,
        competitionName = competitionName,
        commenceTime = commenceTime,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        market = market,
        selection = selection,
        betclicOdds = 0.0,
        impliedProbability = 0.0,
        consensusProbability = 0.63,
        valueEdge = 0.0,
        expectedValue = 0.0,
        confidenceScore = confidenceScore,
        riskLevel = "Moyen",
        category = category,
        bookmakerCount = 0,
        sourceName = sourceName,
        sourceLastUpdate = sourceLastUpdate,
        explanation = "",
        positiveArguments = positiveArguments,
        negativeArguments = negativeArguments,
        expectedScore = expectedScore,
        statSummary = statSummary,
        scenarios = scenarios,
        homeLineupStatus = homeLineupStatus,
        homeLineup = homeLineup,
        awayLineupStatus = awayLineupStatus,
        awayLineup = awayLineup,
        playerScenarios = playerScenarios,
        sourceDetails = sourceDetails,
        contextInsights = contextInsights,
        sourceAgreement = sourceAgreement,
    )

    private fun liveEvent(
        sportKey: String = "racing/f1",
        sportTitle: String = "Formule 1",
        statSummary: String,
        scenarios: String,
        sourceDetails: String,
    ) = LiveEventEntity(
        id = "live-1",
        sportKey = sportKey,
        sportTitle = sportTitle,
        competitionName = "Grand Prix",
        commenceTime = 1_790_000_000_000,
        eventName = "Grand Prix test",
        homeName = "Course",
        awayName = "Peloton",
        homeScore = null,
        awayScore = null,
        statusState = "in",
        statusDescription = "Course en direct",
        displayClock = "tour 57",
        period = null,
        isLive = true,
        sourceName = "Live test",
        sourceDetails = sourceDetails,
        lastUpdate = 1_790_000_000_000,
        statSummary = statSummary,
        scenarios = scenarios,
    )
}
