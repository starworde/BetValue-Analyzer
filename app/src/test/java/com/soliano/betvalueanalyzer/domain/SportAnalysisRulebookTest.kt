package com.soliano.betvalueanalyzer.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportAnalysisRulebookTest {
    @Test
    fun everyCatalogSportHasADedicatedWeakDataRule() {
        SportsCatalog.sports.forEach { sport ->
            val rule = SportAnalysisRulebook.rule(sport.key)
            val text = rule.searchText()

            assertTrue("${sport.key} doit avoir au moins 5 stats attendues", rule.requiredStats.size >= 5)
            assertTrue("${sport.key} doit avoir des stats acteurs/joueurs", rule.actorStats.size >= 3)
            assertTrue("${sport.key} doit avoir des scénarios", rule.scenarios.size >= 3)
            assertTrue("${sport.key} doit rester en statut prudent si incomplet", rule.readiness in setOf(AnalysisReadiness.Weak, AnalysisReadiness.WatchOnly, AnalysisReadiness.Impossible))
            assertTrue("${sport.key} doit expliquer les données manquantes", text.contains("attendre") || text.contains("surveiller") || text.contains("projection"))
        }
    }

    @Test
    fun tennisRulesCompareBothPlayersAndAvoidFootballVocabulary() {
        val text = SportAnalysisRulebook.rule("tennis/atp").searchText()

        listOf("surface", "classement atp/wta", "service", "retour", "fatigue", "h2h").forEach { token ->
            assertTrue("tennis doit contenir $token", text.contains(token))
        }
        listOf("buts", "90 minutes", "prolongation").forEach { forbidden ->
            assertFalse("tennis ne doit pas recycler $forbidden", text.contains(forbidden))
        }
    }

    @Test
    fun raceSportsNeverExposeBallSportMarketsWhenDataIsWeak() {
        listOf("cycling", "racing", "nascar", "golf", "athletics").forEach { sport ->
            val text = SportAnalysisRulebook.rule(sport).searchText()

            assertTrue("$sport doit rester en surveillance", text.contains("surveillance") || text.contains("attendre") || text.contains("pas de"))
            listOf("buts", "score exact", "90 minutes", "prolongation").forEach { forbidden ->
                assertFalse("$sport ne doit pas contenir $forbidden dans $text", text.contains(forbidden))
            }
        }
    }

    @Test
    fun indoorTeamSportsUseTheirOwnUnits() {
        val handball = SportAnalysisRulebook.rule("handball/ehf").searchText()
        val volley = SportAnalysisRulebook.rule("volleyball/vnl").searchText()

        listOf("gardiens", "exclusions 2 minutes", "7m", "buts").forEach { token ->
            assertTrue("handball doit contenir $token", handball.contains(token))
        }
        listOf("sets", "points par set", "service", "réception", "contres").forEach { token ->
            assertTrue("volley doit contenir $token", volley.contains(token))
        }
        assertFalse("volley ne doit pas contenir buts", volley.contains("buts"))
        assertFalse("handball ne doit pas contenir sets", handball.contains("sets"))
    }

    private fun SportAnalysisRule.searchText(): String =
        listOf(
            market,
            selection,
            expectedState,
            explanation,
            requiredStats.joinToString(" "),
            actorStats.joinToString(" "),
            contextStats.joinToString(" "),
            scenarios.joinToString(" ") { "${it.type} ${it.label}" },
        ).joinToString(" ").lowercase()
}
