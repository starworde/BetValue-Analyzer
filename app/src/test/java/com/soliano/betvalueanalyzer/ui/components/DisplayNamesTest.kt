package com.soliano.betvalueanalyzer.ui.components

import com.soliano.betvalueanalyzer.data.local.UpcomingEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayNamesTest {
    @Test
    fun `national teams are displayed and searchable in French`() {
        val event = UpcomingEventEntity(
            id = "soccer-all:test",
            sportKey = "soccer",
            sportTitle = "Football",
            competitionKey = "soccer:world-cup",
            competitionName = "Coupe du monde FIFA",
            commenceTime = 1_783_209_600_000,
            eventName = "Switzerland vs Canada",
            participantA = "Switzerland",
            participantB = "Canada",
            eventType = "MATCH",
            sourceName = "ESPN public",
            analysisId = null,
        )

        assertEquals("Suisse", displayTeamName("Switzerland"))
        assertEquals("Suisse — Canada", displayEventTitle(event))
        assertTrue(matchesSearch(searchableText(event), "suisse"))
        assertTrue(matchesSearch(searchableText(event), "coupe du monde"))
    }

    @Test
    fun `mojibake accents are cleaned before display`() {
        assertEquals("Athlétisme", cleanDisplayText("AthlÃ©tisme"))
        assertEquals("Actualité", cleanDisplayText("ActualitÃ©"))
        assertEquals("Événement suivi", cleanDisplayText("Ã‰vÃ©nement suivi"))
        assertEquals("Cœur du jeu", cleanDisplayText("Cœur du jeu"))
        assertEquals("Œuvre collective", cleanDisplayText("Œuvre collective"))
    }

    @Test
    fun `weak data categories display as clear low data status`() {
        assertEquals("Données faibles", predictionCategoryLabel("Données à compléter"))
        assertEquals("exotique", predictionCategoryKey("Données à compléter"))
        assertEquals("Données faibles", predictionCategoryLabel("Données insuffisantes"))
    }
}
