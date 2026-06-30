package com.soliano.betvalueanalyzer.domain

import com.soliano.betvalueanalyzer.data.local.LiveEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWindowPolicyTest {
    @Test
    fun `live state resolver exposes all required states`() {
        val now = 1_800_000_000_000L
        val footballDuration = 2L * 60L * 60L * 1000L

        assertEquals(
            LiveEventState.Upcoming,
            LiveWindowPolicy.liveState(event(commenceTime = now + 2L * 60L * 60L * 1000L), now).state,
        )
        assertEquals(
            LiveEventState.Soon,
            LiveWindowPolicy.liveState(event(commenceTime = now + 20L * 60L * 1000L), now).state,
        )
        assertEquals(
            LiveEventState.Live,
            LiveWindowPolicy.liveState(event(commenceTime = now - 20L * 60L * 1000L, isLive = true), now).state,
        )
        assertEquals(
            LiveEventState.Finished,
            LiveWindowPolicy.liveState(
                event(
                    commenceTime = now - footballDuration - 20L * 60L * 1000L,
                    statusState = "post",
                    statusDescription = "Final",
                ),
                now,
            ).state,
        )
        assertEquals(
            LiveEventState.ResultConfirmed,
            LiveWindowPolicy.liveState(
                event(
                    commenceTime = now - footballDuration - 20L * 60L * 1000L,
                    statusState = "post",
                    statusDescription = "Final",
                    homeScore = 2,
                    awayScore = 1,
                ),
                now,
            ).state,
        )
        assertEquals(
            LiveEventState.Archived,
            LiveWindowPolicy.liveState(
                event(
                    commenceTime = now - footballDuration - 31L * 60L * 1000L,
                    statusState = "post",
                    statusDescription = "Final",
                ),
                now,
            ).state,
        )
    }

    @Test
    fun `race with final top three becomes confirmed result`() {
        val now = 1_800_000_000_000L
        val racingDuration = 3L * 60L * 60L * 1000L

        val state = LiveWindowPolicy.liveState(
            event(
                sportKey = "racing/f1",
                commenceTime = now - racingDuration - 10L * 60L * 1000L,
                statusState = "post",
                statusDescription = "Race finished",
                statSummary = "Top 3 : Russell, Verstappen, Antonelli",
            ),
            now,
        )

        assertEquals(LiveEventState.ResultConfirmed, state.state)
        assertTrue(state.visibleInLiveTab)
    }

    @Test
    fun `live event is visible during plausible duration and archived after stale window`() {
        val now = 1_800_000_000_000L
        assertTrue(
            LiveWindowPolicy.shouldShow(
                event(commenceTime = now - 60L * 60L * 1000L, isLive = true),
                now,
            )
        )
        assertFalse(
            LiveWindowPolicy.shouldShow(
                event(commenceTime = now - 4L * 60L * 60L * 1000L, isLive = true),
                now,
            )
        )
    }

    @Test
    fun `upcoming event is visible only thirty minutes before start`() {
        val now = 1_800_000_000_000L

        assertTrue(LiveWindowPolicy.shouldShow(event(commenceTime = now + 29L * 60L * 1000L), now))
        assertFalse(LiveWindowPolicy.shouldShow(event(commenceTime = now + 31L * 60L * 1000L), now))
    }

    @Test
    fun `finished event is kept only for thirty minutes after estimated end`() {
        val now = 1_800_000_000_000L
        val footballDuration = 2L * 60L * 60L * 1000L

        assertTrue(
            LiveWindowPolicy.shouldShow(
                event(
                    sportKey = "soccer/all",
                    commenceTime = now - footballDuration - 20L * 60L * 1000L,
                    statusState = "post",
                    statusDescription = "Final",
                ),
                now,
            )
        )
        assertFalse(
            LiveWindowPolicy.shouldShow(
                event(
                    sportKey = "soccer/all",
                    commenceTime = now - footballDuration - 31L * 60L * 1000L,
                    statusState = "post",
                    statusDescription = "Final",
                ),
                now,
            )
        )
    }

    @Test
    fun `non live event already started without final status is hidden`() {
        val now = 1_800_000_000_000L
        assertFalse(
            LiveWindowPolicy.shouldShow(
                event(commenceTime = now - 10L * 60L * 1000L, statusState = "pre", statusDescription = "Attente flux"),
                now,
            )
        )
    }

    private fun event(
        sportKey: String = "soccer/all",
        commenceTime: Long,
        statusState: String = "pre",
        statusDescription: String = "Scheduled",
        isLive: Boolean = false,
        homeScore: Int? = null,
        awayScore: Int? = null,
        statSummary: String = "",
    ) = LiveEventEntity(
        id = "test:$sportKey:$commenceTime",
        sportKey = sportKey,
        sportTitle = sportKey.substringBefore('/'),
        competitionName = "Test",
        commenceTime = commenceTime,
        eventName = "A — B",
        homeName = "A",
        awayName = "B",
        homeScore = homeScore,
        awayScore = awayScore,
        statusState = statusState,
        statusDescription = statusDescription,
        displayClock = "",
        period = null,
        isLive = isLive,
        sourceName = "Test",
        sourceDetails = "Test",
        lastUpdate = commenceTime,
        statSummary = statSummary,
        scenarios = "",
    )
}
