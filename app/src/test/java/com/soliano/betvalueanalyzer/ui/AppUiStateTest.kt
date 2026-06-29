package com.soliano.betvalueanalyzer.ui

import com.soliano.betvalueanalyzer.data.UserSettings
import com.soliano.betvalueanalyzer.data.local.UpcomingEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUiStateTest {
    @Test
    fun `home state separates competition favorite events from sport favorite events`() {
        val favoriteCompetitionEvent = event(
            id = "top14-1",
            sportKey = "rugby",
            competitionKey = "rugby:top-14",
            competitionName = "Top 14",
            commenceTime = 2000L,
        )
        val favoriteSportEvent = event(
            id = "rugby-other",
            sportKey = "rugby",
            competitionKey = "rugby:six-nations",
            competitionName = "Six Nations",
            commenceTime = 1000L,
        )
        val ignoredEvent = event(
            id = "tennis",
            sportKey = "tennis",
            competitionKey = "tennis:roland-garros",
            competitionName = "Roland Garros",
            commenceTime = 500L,
        )
        val state = AppUiState(
            upcomingEvents = listOf(ignoredEvent, favoriteCompetitionEvent, favoriteSportEvent),
            settings = UserSettings(
                favoriteSports = setOf("rugby"),
                favoriteCompetitions = setOf("rugby:top-14"),
            ),
        )

        assertTrue(state.hasConfiguredFavorites)
        assertEquals(listOf(favoriteCompetitionEvent), state.favoriteCompetitionUpcomingEvents)
        assertEquals(listOf(favoriteSportEvent), state.favoriteSportUpcomingEvents)
        assertEquals(listOf(favoriteSportEvent, favoriteCompetitionEvent), state.favoriteUpcomingEvents)
    }

    private fun event(
        id: String,
        sportKey: String,
        competitionKey: String,
        competitionName: String,
        commenceTime: Long,
    ) = UpcomingEventEntity(
        id = id,
        sportKey = sportKey,
        sportTitle = sportKey,
        competitionKey = competitionKey,
        competitionName = competitionName,
        commenceTime = commenceTime,
        eventName = "$competitionName event",
        participantA = "A",
        participantB = "B",
        eventType = "MATCH",
        sourceName = "test",
        analysisId = null,
    )
}
