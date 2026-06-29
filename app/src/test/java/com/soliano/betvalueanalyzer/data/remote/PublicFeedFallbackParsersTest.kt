package com.soliano.betvalueanalyzer.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PublicFeedFallbackParsersTest {
    @Test
    fun tolerantEspnParserIgnoresMalformedOptionalMarkets() {
        val events = EspnFallbackParser.parse(
            """
            {"events":[{"id":"e1","uid":"s:600~l:606~e:e1","date":"2026-06-25T18:00Z","name":"France at Brazil",
            "status":{"type":{"completed":false}},"competitions":[{"id":"c1","competitors":[
            {"homeAway":"home","team":{"displayName":"Brazil"},"form":"WWD"},
            {"homeAway":"away","team":{"displayName":"France"},"form":"LDW"}],
            "odds":[{"overUnder":"not-a-number"}]}]}]}
            """.trimIndent()
        )

        assertEquals("France at Brazil", events.single().name)
        assertEquals(2, events.single().competitions.orEmpty().single().competitors.orEmpty().size)
        assertFalse(events.single().status?.type?.completed ?: true)
    }

    @Test
    fun secondaryScheduleParserExtractsMatchAndLeague() {
        val events = TheSportsDbFallbackParser.parse(
            """
            {"events":[{"idEvent":"42","strTimestamp":"2026-06-25T20:00:00","strEvent":"A vs B",
            "strSport":"Soccer","idLeague":"606","strLeague":"FIFA World Cup",
            "strHomeTeam":"A","strAwayTeam":"B","idHomeTeam":"1","idAwayTeam":"2"}]}
            """.trimIndent()
        )

        assertEquals("2026-06-25T20:00:00Z", events.single().timestamp)
        assertEquals("FIFA World Cup", events.single().leagueName)
        assertEquals("1", events.single().homeTeamId)
    }

    @Test
    fun secondaryScheduleParserBuildsTimestampFromDateAndTime() {
        val events = TheSportsDbFallbackParser.parse(
            """
            {"events":[{"idEvent":"99","dateEvent":"2026-07-02","strTime":"18:30:00","strEvent":"Final race",
            "strSport":"Cycling","idLeague":"4465","strLeague":"UCI World Tour"}]}
            """.trimIndent()
        )

        assertEquals("2026-07-02T18:30:00Z", events.single().timestamp)
        assertEquals("Cycling", events.single().sport)
        assertEquals("UCI World Tour", events.single().leagueName)
    }

    @Test
    fun secondaryScheduleParserKeepsExistingTimezoneOffset() {
        val events = TheSportsDbFallbackParser.parse(
            """
            {"events":[{"idEvent":"2445406","strTimestamp":"2026-07-15T03:00:00+00:00",
            "strEvent":"Canada Volleyball vs Argentina Volleyball",
            "strSport":"Volleyball","idLeague":"5083","strLeague":"FIVB Volleyball Mens Nations League",
            "strHomeTeam":"Canada Volleyball","strAwayTeam":"Argentina Volleyball"}]}
            """.trimIndent()
        )

        assertEquals("2026-07-15T03:00:00+00:00", events.single().timestamp)
        assertEquals("Volleyball", events.single().sport)
        assertEquals("FIVB Volleyball Mens Nations League", events.single().leagueName)
    }

    @Test
    fun secondaryScheduleParserExtractsScoreAndStatusWhenAvailable() {
        val events = TheSportsDbFallbackParser.parse(
            """
            {"events":[{"idEvent":"100","strTimestamp":"2026-06-26T19:00:00","strEvent":"France vs Ireland",
            "strSport":"Rugby","idLeague":"999","strLeague":"Test Rugby",
            "strHomeTeam":"France","strAwayTeam":"Ireland","intHomeScore":"17","intAwayScore":"14",
            "strStatus":"Live","strProgress":"61'"}]}
            """.trimIndent()
        )

        val event = events.single()
        assertEquals(17, event.homeScore)
        assertEquals(14, event.awayScore)
        assertEquals("Live", event.status)
        assertEquals("61'", event.progress)
    }

    @Test
    fun tolerantEspnParserExtractsRaceSessionAndOrder() {
        val events = EspnFallbackParser.parse(
            """
            {"events":[{"id":"gp1","date":"2026-06-26T11:30Z","endDate":"2026-06-28T13:00Z","name":"Austrian Grand Prix",
            "competitions":[{"id":"qual1","date":"2026-06-27T14:00Z","type":{"id":"2","abbreviation":"Qual"},
            "status":{"type":{"completed":true,"state":"post","description":"Final"}},
            "competitors":[
            {"order":1,"athlete":{"displayName":"Driver A"}},
            {"order":2,"athlete":{"displayName":"Driver B"}},
            {"order":3,"athlete":{"displayName":"Driver C"}}]}]}]}
            """.trimIndent()
        )

        val competition = events.single().competitions.orEmpty().single()
        assertEquals("2026-06-28T13:00Z", events.single().endDate)
        assertEquals("Qual", competition.type?.abbreviation)
        assertEquals(1, competition.competitors.orEmpty().first().order)
        assertEquals("Driver A", competition.competitors.orEmpty().first().athlete?.displayName)
    }
}
