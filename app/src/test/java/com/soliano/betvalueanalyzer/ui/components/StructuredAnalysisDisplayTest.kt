package com.soliano.betvalueanalyzer.ui.components

import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredAnalysisDisplayTest {
    @Test
    fun `dossier follows mandatory analysis order and separates context from probabilities`() {
        val prediction = prediction(
            statSummary = """
                France : 6 matchs, 2,1 buts marqués et 0,8 encaissés
                Allemagne : classement 3e du groupe, doit absolument gagner
                Germany : 5 matchs, 1,4 buts marqués et 1,6 encaissés
                Forme France : tendance positive sur les 5 derniers matchs
                Forme Allemagne : tendance irrégulière sur les 5 derniers matchs
                Domicile/extérieur : France à domicile
                Stats clés surveillées : tirs, tirs cadrés, corners, cartons
            """.trimIndent(),
            contextInsights = """
                Mbappé : retour de blessure confirmé par conférence de presse
                Mbappé : 87 minutes moyennes, fatigue moyenne
                Rotation possible côté France
                France : coach absent communiqué officiel
                Météo défavorable possible
            """.trimIndent(),
            scenarios = """
                Score exact conditionnel|Score probable 2-1|0.14
                Total|Plus de 2,5 buts|0.54
                Corners|Plus de 8,5 corners|0.52
                Discipline|Carton rouge|0.18
                Équipe|France marque au moins 1 but|0.78
            """.trimIndent(),
            playerScenarios = "Joueur|Mbappé but ou passe|0.57",
            sourceDetails = """
                Site officiel FIFA
                ESPN public
                TheSportsDB public
                Instagram officiel France
                Météo publique
            """.trimIndent(),
        )

        val dossier = prediction.structuredAnalysisDossier()

        assertTrue(dossier.participantSituation.first().contains("Football"))
        val franceSituationBlock = dossier.participantSituationBlocks.single { it.name == "France" }
        val germanySituationBlock = dossier.participantSituationBlocks.single { it.name == "Allemagne" }
        assertTrue(franceSituationBlock.lines.any { it.contains("domicile", ignoreCase = true) })
        assertTrue(franceSituationBlock.lines.any { it.contains("composition", ignoreCase = true) || it.contains("faire tourner", ignoreCase = true) })
        assertTrue(germanySituationBlock.lines.any { it.contains("doit absolument gagner", ignoreCase = true) })
        assertTrue(germanySituationBlock.lines.any { it.contains("volume offensif", ignoreCase = true) || it.contains("Impact concret", ignoreCase = true) })
        assertTrue(dossier.competitionSituation.any { it.contains("doit absolument gagner", ignoreCase = true) })
        val franceStakeBlock = dossier.competitionStakeBlocks.single { it.name == "France" }
        val germanyStakeBlock = dossier.competitionStakeBlocks.single { it.name == "Allemagne" }
        assertTrue(franceStakeBlock.statuses.contains("Rotation possible"))
        assertTrue(franceStakeBlock.reading.contains("titulaires", ignoreCase = true) || franceStakeBlock.reading.contains("fragiles", ignoreCase = true))
        assertTrue(franceStakeBlock.reason.contains("préserver", ignoreCase = true) || franceStakeBlock.reason.contains("rotation", ignoreCase = true))
        assertTrue(franceStakeBlock.forecastImpact.any { it.contains("titulaires", ignoreCase = true) || it.contains("joueurs", ignoreCase = true) })
        assertTrue(germanyStakeBlock.statuses.contains("Doit absolument gagner"))
        assertTrue(germanyStakeBlock.reading.contains("faits mesurables", ignoreCase = true) || germanyStakeBlock.reading.contains("tirs", ignoreCase = true))
        assertTrue(germanyStakeBlock.reason.contains("volume offensif", ignoreCase = true) || germanyStakeBlock.reason.contains("points", ignoreCase = true))
        assertTrue(germanyStakeBlock.forecastImpact.any { it.contains("volume", ignoreCase = true) || it.contains("espaces", ignoreCase = true) })
        assertTrue(dossier.competitionImpact.any { it.contains("prise de risque", ignoreCase = true) })
        assertTrue(dossier.competitionImpact.any { it.contains("Allemagne") })
        val visibleCompetitionText = (
            dossier.competitionStakeBlocks.flatMap { listOf(it.reading, it.reason) + it.forecastImpact } +
                dossier.competitionImpact
            ).joinToString(" ").let(::cleanDisplayText)
        assertTrue(!visibleCompetitionText.contains("motivation haute", ignoreCase = true))
        assertTrue(!visibleCompetitionText.contains("pression/motivation", ignoreCase = true))
        assertTrue(!visibleCompetitionText.contains("enjeu exact non prouvé", ignoreCase = true))
        assertTrue(!visibleCompetitionText.contains("attendre", ignoreCase = true))
        assertTrue(!visibleCompetitionText.contains("à recalculer", ignoreCase = true))
        assertTrue(!visibleCompetitionText.contains("a recalculer", ignoreCase = true))
        val franceBlock = dossier.participantBlocks.single { it.name == "France" }
        val germanyBlock = dossier.participantBlocks.single { it.name == "Allemagne" }
        assertTrue(franceBlock.stats.any { it.contains("6 matchs") })
        assertTrue(germanyBlock.stats.any { it.contains("5 matchs") })
        val franceMissingStats = dossier.participantStatRequirementBlocks
            .single { it.name == "France" }
            .missingStats.joinToString(" ")
            .let(::cleanDisplayText)
        assertTrue(franceMissingStats.contains("tirs", ignoreCase = true))
        assertTrue(franceMissingStats.contains("corners", ignoreCase = true))
        assertTrue(franceMissingStats.contains("cartons", ignoreCase = true))
        assertTrue(franceBlock.probabilities.any { it.label.contains("France marque") })
        val franceFormBlock = dossier.formTrendBlocks.single { it.name == "France" }
        val germanyFormBlock = dossier.formTrendBlocks.single { it.name == "Allemagne" }
        assertTrue(franceFormBlock.trends.any { it.contains("tendance positive", ignoreCase = true) })
        assertTrue(germanyFormBlock.trends.any { it.contains("tendance irrégulière", ignoreCase = true) })
        assertTrue(dossier.formTrends.none { it.contains("Forme France", ignoreCase = true) })
        val franceImportantBlock = dossier.importantInfoBlocks.single { it.name == "France" }
        val mbappeImportantBlock = dossier.importantInfoBlocks.single { it.name == "Mbappé" }
        assertTrue(franceImportantBlock.items.any { it.text.contains("Rotation possible", ignoreCase = true) })
        assertTrue(franceImportantBlock.items.any { it.category.contains("Coach", ignoreCase = true) && it.level == "Confirmé officiel" })
        assertTrue(mbappeImportantBlock.items.any { it.text.contains("retour de blessure", ignoreCase = true) })
        assertTrue(mbappeImportantBlock.items.any { it.category.contains("Infirmerie", ignoreCase = true) })
        assertTrue(mbappeImportantBlock.items.any { it.level == "Confirmé officiel" })
        assertTrue(dossier.importantInfo.none { it.text.contains("retour de blessure", ignoreCase = true) })
        assertTrue(dossier.importantInfo.any { it.category.contains("Météo", ignoreCase = true) && it.text.contains("défavorable", ignoreCase = true) })
        assertTrue(dossier.sourceReliability.any { it.level == "Très probable" })
        assertTrue(dossier.sourceCoverage.any { it.family.contains("Sites officiels") && it.status == "Couvert" })
        assertTrue(dossier.sourceCoverage.any { it.family.contains("Statistiques") && it.status == "Couvert" })
        assertTrue(dossier.sourceCoverage.any { it.family.contains("Réseaux") && it.status == "Couvert" })
        assertTrue(dossier.sourceCoverage.any { it.family.contains("Météo") && it.status == "Couvert" })
        assertEquals("Signal principal", dossier.globalProbabilities.first().type)
        val globalGroupTitles = dossier.globalProbabilityGroups.map { it.title }
        assertTrue(globalGroupTitles.contains("Résultat & score"))
        assertTrue(globalGroupTitles.contains("Totaux"))
        assertTrue(globalGroupTitles.contains("Stats de match"))
        assertTrue(globalGroupTitles.contains("Discipline & contexte"))
        assertTrue(dossier.globalMarketRequirements.any { it.family == "Score / état final" && it.status == "Couvert" })
        assertTrue(dossier.globalMarketRequirements.any { it.family == "Double chance" && it.status == "À recouper" })
        assertTrue(dossier.globalMarketRequirements.any { it.family == "Stats de match" && it.markets.contains("corners", ignoreCase = true) })
        assertTrue(dossier.globalMarketRequirements.any { it.family == "Discipline / événements" && it.status == "Couvert" })
        assertTrue(dossier.participantProbabilities.any { it.label.contains("France marque") })
        val franceHintBlock = dossier.participantProbabilityHintBlocks.single { it.name == "France" }
        val franceHints = franceHintBlock.hints.joinToString(" ") { "${it.type} ${it.label}" }.let(::cleanDisplayText)
        assertTrue(franceHints.contains("marque au moins 1 but", ignoreCase = true))
        assertTrue(franceHints.contains("corners", ignoreCase = true))
        assertTrue(franceHints.contains("carton", ignoreCase = true))
        assertTrue(dossier.playerProbabilities.any { it.label.contains("Mbappé") })
        val mbappeBlock = dossier.actorBlocks.single { it.name == "Mbappé" }
        assertTrue(mbappeBlock.teamOrRole == "France")
        assertTrue(mbappeBlock.situation.any { it.contains("Probable") })
        assertTrue(mbappeBlock.situation.any { it.contains("fatigue moyenne", ignoreCase = true) })
        assertTrue(mbappeBlock.importantInfo.any { it.text.contains("retour de blessure", ignoreCase = true) })
        assertTrue(mbappeBlock.importantInfo.any { it.level == "Confirmé officiel" })
        assertTrue(mbappeBlock.probabilities.any { it.label.contains("but ou passe") })
        val mbappeMissingStats = dossier.actorStatRequirementBlocks
            .single { it.name == "Mbappé" }
            .missingStats.joinToString(" ")
            .let(::cleanDisplayText)
        assertTrue(mbappeMissingStats.contains("buts", ignoreCase = true))
        assertTrue(mbappeMissingStats.contains("passes", ignoreCase = true))
        assertTrue(mbappeMissingStats.contains("temps de jeu", ignoreCase = true))
        val finalTitles = dossier.finalExplanationBlocks.map { it.title }
        assertTrue(finalTitles.contains("Lecture principale"))
        assertTrue(finalTitles.contains("Pourquoi ce scénario ressort"))
        assertTrue(finalTitles.contains("Ce qui augmente la probabilité"))
        assertTrue(finalTitles.contains("Ce qui baisse la probabilité / vigilance"))
        assertTrue(finalTitles.contains("Pourquoi le contexte change le pronostic"))
        assertTrue(finalTitles.contains("Confiance de l’analyse"))
        assertTrue(dossier.finalExplanationBlocks.any { block ->
            block.title == "Joueurs / pilotes / acteurs clés" && block.lines.any { it.contains("Mbappé") }
        })
        assertTrue(dossier.sportSpecificChecklist.any { it.startsWith("Stats clés") })
    }

    @Test
    fun `dossier estimates form trend for every participant without explicit form lines`() {
        val prediction = prediction(
            homeTeam = "Ajax",
            awayTeam = "PSV",
            selection = "Ajax",
            statSummary = """
                Ajax : 5 matchs, 4 victoires, 11 buts marqués
                PSV : 5 matchs, 3 défaites, encaisse plus depuis deux rencontres
            """.trimIndent(),
            contextInsights = "",
            scenarios = "Résultat|Ajax gagne|0.61",
            playerScenarios = "",
        )

        val dossier = prediction.structuredAnalysisDossier()
        val ajaxTrend = dossier.formTrendBlocks.single { it.name == "Ajax" }
        val psvTrend = dossier.formTrendBlocks.single { it.name == "PSV" }

        assertTrue(ajaxTrend.trends.any { it.contains("Tendance estimée", ignoreCase = true) && it.contains("hausse", ignoreCase = true) })
        assertTrue(ajaxTrend.trends.any { it.contains("Pourquoi", ignoreCase = true) && it.contains("4 victoires", ignoreCase = true) })
        assertTrue(psvTrend.trends.any { it.contains("Tendance estimée", ignoreCase = true) && it.contains("baisse", ignoreCase = true) })
        assertTrue(psvTrend.trends.none { it.contains("Contrôle PDF", ignoreCase = true) })
    }

    @Test
    fun `dossier adds sport specific checklist when public data is still incomplete`() {
        val prediction = prediction(
            sportKey = "cycling/uci",
            sportTitle = "Cyclisme",
            competitionName = "Tour de France",
            homeTeam = "Tour de France · FRA",
            awayTeam = "",
            selection = "Attendre startlist officielle",
            statSummary = """
                Course : Tour de France · FRA
                Date détectée : 2026-07-04
                Statut : surveillance jusqu'à startlist/favoris/parcours consolidés
            """.trimIndent(),
            contextInsights = "Météo et rôles d'équipe à recouper avant signal fort",
            scenarios = "Course cycliste|Course confirmée au calendrier public|1.0",
            playerScenarios = "",
            homeLineupStatus = "",
            homeLineup = "",
        )

        val dossier = prediction.structuredAnalysisDossier()
        val checklist = dossier.sportSpecificChecklist.joinToString(" ").let(::cleanDisplayText)

        assertTrue(checklist.contains("startlist", ignoreCase = true))
        assertTrue(checklist.contains("parcours", ignoreCase = true))
        assertTrue(checklist.contains("rôles", ignoreCase = true) || checklist.contains("roles", ignoreCase = true))
        assertTrue(checklist.contains("météo", ignoreCase = true) || checklist.contains("meteo", ignoreCase = true))
        assertTrue(dossier.sportSpecificChecklist.none { it.contains("tirs cadrés", ignoreCase = true) })
        val cyclingMissingStats = dossier.participantStatRequirementBlocks.single().missingStats
            .joinToString(" ")
            .let(::cleanDisplayText)
        assertTrue(cyclingMissingStats.contains("startlist", ignoreCase = true))
        assertTrue(cyclingMissingStats.contains("parcours", ignoreCase = true))
        assertTrue(cyclingMissingStats.contains("dénivelé", ignoreCase = true) || cyclingMissingStats.contains("denivele", ignoreCase = true))
        assertTrue(dossier.sourceCoverage.any { it.family.contains("Réseaux") && it.status == "À recouper" })
        assertTrue(dossier.sportProbabilityHints.any { it.label.contains("podium", ignoreCase = true) || it.type.contains("podium", ignoreCase = true) })
        assertTrue(dossier.globalMarketRequirements.any { it.family == "Classement course" && it.markets.contains("top 10", ignoreCase = true) })
        assertTrue(dossier.globalMarketRequirements.any { it.family == "Scénario de course" && it.markets.contains("échappée", ignoreCase = true) })
        assertTrue(dossier.globalMarketRequirements.none { it.markets.contains("buts", ignoreCase = true) })
        assertTrue(dossier.sportActorProbabilityHints.any { it.label.contains("Coureur", ignoreCase = true) || it.type.contains("Coureur", ignoreCase = true) })
        val actorRequirements = dossier.actorStatRequirements.joinToString(" ").let(::cleanDisplayText)
        assertTrue(actorRequirements.contains("forme coureur", ignoreCase = true))
        assertTrue(actorRequirements.contains("leader", ignoreCase = true) || actorRequirements.contains("équipier", ignoreCase = true))
        assertTrue(actorRequirements.contains("sprint", ignoreCase = true))
        val cyclingParticipantHints = dossier.participantProbabilityHintBlocks.single().hints
            .joinToString(" ") { "${it.type} ${it.label}" }
            .let(::cleanDisplayText)
        assertTrue(cyclingParticipantHints.contains("top 10", ignoreCase = true) || cyclingParticipantHints.contains("podium", ignoreCase = true))
        assertTrue(cyclingParticipantHints.contains("duel", ignoreCase = true))
        assertTrue(cyclingParticipantHints.contains("abandon", ignoreCase = true))
    }

    @Test
    fun `rugby dossier exposes rugby probability hints instead of football markets`() {
        val prediction = prediction(
            sportKey = "rugby/all",
            sportTitle = "Rugby",
            competitionName = "Top 14",
            homeTeam = "Toulouse",
            awayTeam = "La Rochelle",
            selection = "Toulouse",
            statSummary = "Toulouse : forme positive\nLa Rochelle : discipline à surveiller",
            contextInsights = "Météo pluvieuse possible",
            scenarios = "Résultat rugby|Toulouse gagne|0.61",
            playerScenarios = "Joueur · Essai|Finisseur essai|0.55",
        )

        val dossier = prediction.structuredAnalysisDossier()
        val globalHints = dossier.sportProbabilityHints.joinToString(" ") { "${it.type} ${it.label}" }.let(::cleanDisplayText)
        val actorHints = dossier.sportActorProbabilityHints.joinToString(" ") { "${it.type} ${it.label}" }.let(::cleanDisplayText)

        assertTrue(globalHints.contains("essais", ignoreCase = true))
        assertTrue(globalHints.contains("pénalités", ignoreCase = true) || globalHints.contains("penalites", ignoreCase = true))
        assertTrue(globalHints.contains("mêlées", ignoreCase = true) || globalHints.contains("melees", ignoreCase = true))
        assertTrue(actorHints.contains("essai", ignoreCase = true))
        assertTrue(globalHints.contains("Score exact", ignoreCase = true).not())
        assertTrue(dossier.globalMarketRequirements.any { it.family == "Essais / points rugby" && it.markets.contains("transformations", ignoreCase = true) })
        assertTrue(dossier.globalMarketRequirements.any { it.family == "Discipline rugby" && it.markets.contains("cartons", ignoreCase = true) })
        assertTrue(dossier.globalMarketRequirements.none { it.markets.contains("corners", ignoreCase = true) })
        val rugbyMissingStats = dossier.participantStatRequirementBlocks
            .single { it.name == "Toulouse" }
            .missingStats.joinToString(" ")
            .let(::cleanDisplayText)
        assertTrue(rugbyMissingStats.contains("essais", ignoreCase = true))
        assertTrue(rugbyMissingStats.contains("touches", ignoreCase = true))
        assertTrue(rugbyMissingStats.contains("mêlées", ignoreCase = true) || rugbyMissingStats.contains("melees", ignoreCase = true))
        val rugbyParticipantHints = dossier.participantProbabilityHintBlocks
            .single { it.name == "Toulouse" }
            .hints.joinToString(" ") { "${it.type} ${it.label}" }
            .let(::cleanDisplayText)
        assertTrue(rugbyParticipantHints.contains("essai", ignoreCase = true))
        assertTrue(rugbyParticipantHints.contains("pénalités", ignoreCase = true) || rugbyParticipantHints.contains("penalites", ignoreCase = true))
        assertTrue(rugbyParticipantHints.contains("mêlées", ignoreCase = true) || rugbyParticipantHints.contains("melees", ignoreCase = true))
        assertTrue(rugbyParticipantHints.contains("corners", ignoreCase = true).not())
    }

    private fun prediction(
        sportKey: String = "soccer/all",
        sportTitle: String = "Football",
        competitionName: String = "Coupe du monde FIFA",
        homeTeam: String = "France",
        awayTeam: String = "Germany",
        selection: String = "France",
        homeLineupStatus: String = "Probable",
        homeLineup: String = "#10|Mbappé|ATT",
        awayLineupStatus: String = "",
        awayLineup: String = "",
        sourceDetails: String = "ESPN public\nTheSportsDB public",
        statSummary: String,
        contextInsights: String,
        scenarios: String,
        playerScenarios: String,
    ) = PredictionEntity(
        id = "p1",
        eventId = "e1",
        sportKey = sportKey,
        sportTitle = sportTitle,
        competitionName = competitionName,
        commenceTime = 1_783_209_600_000,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        market = "Résultat final",
        selection = selection,
        betclicOdds = 2.1,
        impliedProbability = 0.47,
        consensusProbability = 0.54,
        valueEdge = 0.07,
        expectedValue = 0.12,
        confidenceScore = 78,
        riskLevel = "Modéré",
        category = "Fort potentiel",
        bookmakerCount = 0,
        sourceName = "Consensus public",
        sourceLastUpdate = 1_783_209_600_000,
        explanation = "France garde un léger avantage, mais l'Allemagne doit attaquer.",
        positiveArguments = "France à domicile\nMeilleure dynamique offensive",
        negativeArguments = "Rotation possible",
        expectedScore = "2 — 1",
        statSummary = statSummary,
        scenarios = scenarios,
        homeLineupStatus = homeLineupStatus,
        homeLineup = homeLineup,
        awayLineupStatus = awayLineupStatus,
        awayLineup = awayLineup,
        playerScenarios = playerScenarios,
        sourceDetails = sourceDetails,
        contextInsights = contextInsights,
        sourceAgreement = 82,
    )
}
