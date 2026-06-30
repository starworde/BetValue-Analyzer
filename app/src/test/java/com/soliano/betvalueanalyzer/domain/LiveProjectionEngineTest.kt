package com.soliano.betvalueanalyzer.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class LiveProjectionEngineTest {
    @Test
    fun `every catalog sport exposes live probabilities and watched stats`() {
        SportsCatalog.sports.forEach { sport ->
            val projection = LiveProjectionEngine.analyze(
                LiveEventSnapshot(
                    sportKey = "${sport.key}/all",
                    sportTitle = sport.name,
                    homeName = "Participant A",
                    awayName = "Participant B",
                    homeScore = 1,
                    awayScore = 1,
                    statusState = "in",
                    statusDescription = "En direct",
                    displayClock = "45'",
                    period = 2,
                )
            )
            val insight = SportIntelligenceCatalog.profile(sport.key)

            assertTrue("${sport.key} doit avoir des stats live", projection.statSummary.size >= 6)
            assertTrue("${sport.key} doit avoir assez de scénarios live", projection.scenarios.size >= 10)
            assertTrue("${sport.key} doit avoir une fiche stats riche", insight.watchedStats.size >= 9)
            assertTrue("${sport.key} doit avoir des angles joueurs", insight.playerStats.size >= 8)
            assertTrue("${sport.key} doit avoir plusieurs scénarios joueurs", insight.playerProbabilityScenarios.size >= 4)
        }
    }

    @Test
    fun `dedicated live profiles keep sport specific stat vocabulary`() {
        val expectedTokens = mapOf(
            "baseball" to listOf("bullpen", "home run", "strikeouts"),
            "hockey" to listOf("power play", "gardien", "tirs"),
            "field_hockey" to listOf("penalty corner", "carton", "possession"),
            "football" to listOf("touchdown", "turnover", "red zone"),
            "australian_football" to listOf("goals", "inside 50", "disposals"),
            "handball" to listOf("exclusion", "gardien", "7m"),
            "volleyball" to listOf("aces", "réception", "contres"),
            "cricket" to listOf("wicket", "run rate", "batteur"),
            "darts" to listOf("180", "checkout", "legs"),
            "snooker" to listOf("frames", "century", "sécurité"),
            "athletics" to listOf("startlist", "season best", "qualification"),
        )

        expectedTokens.forEach { (sportKey, tokens) ->
            val projection = LiveProjectionEngine.analyze(
                LiveEventSnapshot(
                    sportKey = "$sportKey/all",
                    sportTitle = sportKey,
                    homeName = "Participant A",
                    awayName = "Participant B",
                    homeScore = 1,
                    awayScore = 1,
                    statusState = "in",
                    statusDescription = "Live",
                    displayClock = "20'",
                    period = 1,
                )
            )
            val text = (projection.statSummary + projection.scenarios.map { "${it.type} ${it.label}" }).joinToString(" ").lowercase()
            tokens.forEach { token ->
                assertTrue("$sportKey doit contenir '$token' dans $text", text.contains(token))
            }
        }
    }

    @Test
    fun `rugby live uses rugby specific vocabulary`() {
        val projection = LiveProjectionEngine.analyze(
            LiveEventSnapshot(
                sportKey = "rugby/all",
                sportTitle = "Rugby",
                homeName = "France",
                awayName = "Irlande",
                homeScore = 17,
                awayScore = 14,
                statusState = "in",
                statusDescription = "2e mi-temps",
                displayClock = "61'",
                period = 2,
            )
        )

        val text = (projection.statSummary + projection.scenarios.map { "${it.type} ${it.label}" }).joinToString(" ").lowercase()
        assertTrue(text.contains("essai") || text.contains("essais"))
        assertTrue(text.contains("pénalité") || text.contains("pénalités"))
        assertTrue(text.contains("touche") || text.contains("touches"))
        assertTrue(text.contains("mêlée") || text.contains("mêlées"))
        assertTrue(text.contains("synthèse live") || text.contains("synthese live"))
        assertTrue(text.contains("angles live"))
        assertTrue(text.contains("données live utiles") || text.contains("donnees live utiles"))
        assertTrue(!text.contains("à surveiller"))
        assertTrue(!text.contains("à recouper"))
        assertTrue(text.contains("transformations"))
    }

    @Test
    fun `football live keeps football score vocabulary`() {
        val projection = LiveProjectionEngine.analyze(
            LiveEventSnapshot(
                sportKey = "soccer/all",
                sportTitle = "Football",
                homeName = "France",
                awayName = "Pays-Bas",
                homeScore = 1,
                awayScore = 0,
                statusState = "in",
                statusDescription = "Première mi-temps",
                displayClock = "34'",
                period = 1,
            )
        )

        val text = (projection.statSummary + projection.scenarios.map { "${it.type} ${it.label}" }).joinToString(" ").lowercase()
        assertTrue(text.contains("buts"))
        assertTrue(text.contains("tirs"))
        assertTrue(text.contains("corners"))
        assertTrue(projection.scenarios.any { it.type == "Prochain but" })
    }

    @Test
    fun `race sports live avoid fake score betting vocabulary`() {
        val projection = LiveProjectionEngine.analyze(
            LiveEventSnapshot(
                sportKey = "cycling/uci",
                sportTitle = "Cyclisme",
                homeName = "Tour de France",
                awayName = "Peloton",
                homeScore = null,
                awayScore = null,
                statusState = "pre",
                statusDescription = "Course à surveiller",
                displayClock = "",
                period = null,
            )
        )

        val text = (projection.statSummary + projection.scenarios.map { "${it.type} ${it.label}" }).joinToString(" ").lowercase()
        assertTrue(text.contains("classement") || text.contains("podium"))
        assertTrue(text.contains("synthèse live") || text.contains("synthese live"))
        assertTrue(text.contains("top 3") || text.contains("top 10"))
        assertTrue(text.contains("échappée") || text.contains("echappee"))
        assertTrue(!text.contains("prochain but"))
        assertTrue(!text.contains("total final supérieur"))
    }

    @Test
    fun `race sports live display official top three when available`() {
        val projection = LiveProjectionEngine.analyze(
            LiveEventSnapshot(
                sportKey = "racing/f1",
                sportTitle = "Formule 1",
                homeName = "Grand Prix d'Autriche",
                awayName = "Qualifications",
                homeScore = null,
                awayScore = null,
                statusState = "post",
                statusDescription = "Qualifications · Final",
                displayClock = "",
                period = null,
                resultSummary = "Qualifications : 1. Verstappen · 2. Norris · 3. Leclerc",
            )
        )

        val text = (projection.statSummary + projection.scenarios.map { "${it.type} ${it.label}" }).joinToString(" ")
        assertTrue(text.contains("Top 3"))
        assertTrue(text.contains("Verstappen"))
        assertTrue(text.contains("Norris"))
        assertTrue(!text.lowercase().contains("score réel confirmé"))
    }

    @Test
    fun `team sport live includes home away context`() {
        val projection = LiveProjectionEngine.analyze(
            LiveEventSnapshot(
                sportKey = "basketball/nba",
                sportTitle = "Basketball",
                homeName = "Nuggets",
                awayName = "Suns",
                homeScore = 70,
                awayScore = 68,
                statusState = "in",
                statusDescription = "3e quart-temps",
                displayClock = "08:12",
                period = 3,
            )
        )

        val text = (projection.statSummary + projection.scenarios.map { "${it.type} ${it.label}" }).joinToString(" ")
        assertTrue(text.contains("Domicile/extérieur"))
        assertTrue(text.contains("Nuggets reçoit"))
    }
}
