package com.soliano.betvalueanalyzer.ui.components

import com.soliano.betvalueanalyzer.data.local.LiveEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveEventDisplayTest {
    @Test
    fun `race event displays top three instead of fake score`() {
        val event = liveEvent(
            sportKey = "racing/f1",
            eventName = "Grand Prix d'Autriche",
            homeName = "Grand Prix d'Autriche",
            awayName = "Qualifications",
            homeScore = null,
            awayScore = null,
            statSummary = "Top 3 / classement public : Qualifications : 1. Verstappen · 2. Norris · 3. Leclerc",
        )

        assertTrue(event.isResultBoardEvent())
        assertEquals("Top 3 / classement", event.liveMainMetricLabel())
        assertTrue(event.liveMainMetricText().contains("Verstappen"))
        assertFalse(event.liveMainMetricText().contains("—"))
    }

    @Test
    fun `team event displays confirmed score`() {
        val event = liveEvent(
            sportKey = "soccer/all",
            homeName = "France",
            awayName = "Germany",
            homeScore = 2,
            awayScore = 1,
        )

        assertFalse(event.isResultBoardEvent())
        assertEquals("Score réel", event.liveMainMetricLabel())
        assertTrue(event.liveMainMetricText().contains("2 — 1"))
    }

    @Test
    fun `team event without score is explicit instead of blank dashes`() {
        val event = liveEvent(
            sportKey = "rugby/all",
            homeName = "France",
            awayName = "Irlande",
            homeScore = null,
            awayScore = null,
        )

        assertFalse(event.hasConfirmedScore())
        assertEquals("Score officiel en attente des flux publics", event.liveMainMetricText())
    }

    @Test
    fun `racing event exposes lap progress and remaining laps`() {
        val event = liveEvent(
            sportKey = "racing/f1",
            eventName = "Grand Prix d'Autriche",
            homeName = "Grand Prix d'Autriche",
            awayName = "Course",
            statSummary = """
                Top 3 / classement public : 1. Verstappen · 2. Norris · 3. Leclerc
                Avancement course : Lap 23/71
            """.trimIndent(),
        )

        assertEquals("Tours restants / session", event.liveProgressLabel())
        assertTrue(event.liveProgressText().contains("Tour 23/71"))
        assertTrue(event.liveProgressText().contains("48 tours restants"))
    }

    @Test
    fun `cycling event exposes remaining kilometres`() {
        val event = liveEvent(
            sportKey = "cycling/uci",
            eventName = "Tour de France - Etape 1",
            homeName = "Tour de France",
            awayName = "Etape 1",
            statSummary = """
                Classement public : 1. Pogacar · 2. Vingegaard · 3. Evenepoel
                Distance restante : 12,5 km
            """.trimIndent(),
        )

        assertEquals("Km restants / course", event.liveProgressLabel())
        assertTrue(event.liveProgressText().contains("12,5 km"))
    }

    private fun liveEvent(
        sportKey: String,
        eventName: String = "Match",
        homeName: String = "A",
        awayName: String = "B",
        homeScore: Int? = null,
        awayScore: Int? = null,
        statSummary: String = "",
    ) = LiveEventEntity(
        id = "test:$sportKey:$eventName",
        sportKey = sportKey,
        sportTitle = sportKey.substringBefore('/'),
        competitionName = "Compétition",
        commenceTime = 1_783_209_600_000,
        eventName = eventName,
        homeName = homeName,
        awayName = awayName,
        homeScore = homeScore,
        awayScore = awayScore,
        statusState = "in",
        statusDescription = "En direct",
        displayClock = "45'",
        period = 1,
        isLive = true,
        sourceName = "Test",
        sourceDetails = "Test",
        lastUpdate = 1_783_209_600_000,
        statSummary = statSummary,
        scenarios = "",
    )
}
