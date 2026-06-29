package com.soliano.betvalueanalyzer.data.remote

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicSportsDtoTest {
    @Test
    fun nullableCollectionsFromPublicFeedAreHandled() {
        val json = """
            {
              "events": [{
                "id": "42",
                "date": "2026-06-24T19:00Z",
                "competitions": [{"id": "main", "competitors": null, "odds": null}]
              }]
            }
        """.trimIndent()

        val scoreboard = Gson().fromJson(json, EspnScoreboardDto::class.java)
        assertEquals("42", scoreboard.events.orEmpty().first().id)
        assertNull(scoreboard.events.orEmpty().first().competitions.orEmpty().first().odds)
    }
}
