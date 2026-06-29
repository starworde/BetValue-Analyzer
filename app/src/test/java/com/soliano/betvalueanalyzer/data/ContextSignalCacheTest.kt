package com.soliano.betvalueanalyzer.data

import com.soliano.betvalueanalyzer.domain.MatchContextSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextSignalCacheTest {
    private val now = 1_800_000_000_000L
    private val request = NewsContextRequest(
        key = "soccer|france-norway|match",
        homeTeam = "France",
        awayTeam = "Norway",
        commenceTime = now + 20L * 24 * 60 * 60 * 1000,
    )

    @Test
    fun `confirmed information published two weeks earlier survives an empty refresh`() {
        val stored = persisted(
            title = "Didier Deschamps absent pour France - Norvège",
            publishedAt = now - 14L * 24 * 60 * 60 * 1000,
        )

        val result = ContextSignalCache.merge(listOf(request), emptyMap(), listOf(stored), now)

        val signals = result.reports.getValue(request.key).signals
        assertEquals(1, signals.size)
        assertEquals(stored.title, signals.single().title)
    }

    @Test
    fun `newer return for same person cancels the retained absence`() {
        val stored = persisted(
            title = "Didier Deschamps absent pour France - Norvège",
            publishedAt = now - 14L * 24 * 60 * 60 * 1000,
        )
        val returned = MatchContextSignal(
            teamName = "France",
            title = "Didier Deschamps de retour avec les Bleus",
            publishers = listOf("Média A", "Média B"),
            impact = 0.02,
            category = "Retour important",
            publishedAt = now,
        )

        val result = ContextSignalCache.merge(
            requests = listOf(request),
            fresh = mapOf(request.key to NewsContextReport(listOf(returned))),
            stored = listOf(stored),
            now = now,
        )

        val signals = result.reports.getValue(request.key).signals
        assertFalse(signals.any { it.impact < 0 })
        assertTrue(signals.any { it.impact > 0 })
    }

    @Test
    fun `return of another person does not cancel an unrelated absence`() {
        val stored = persisted(
            title = "Kylian Mbappé forfait avec la France",
            publishedAt = now - 14L * 24 * 60 * 60 * 1000,
            category = "Absence ou blessure",
        )
        val returned = MatchContextSignal(
            teamName = "France",
            title = "Antoine Griezmann de retour avec la France",
            publishers = listOf("Média A", "Média B"),
            impact = 0.02,
            category = "Retour important",
            publishedAt = now,
        )

        val result = ContextSignalCache.merge(
            listOf(request),
            mapOf(request.key to NewsContextReport(listOf(returned))),
            listOf(stored),
            now,
        )

        assertTrue(result.reports.getValue(request.key).signals.any { it.impact < 0 })
    }

    @Test
    fun `cached information is removed after the match`() {
        val finishedRequest = request.copy(commenceTime = now - 3L * 60 * 60 * 1000)
        val stored = persisted(
            title = "Didier Deschamps absent pour France - Norvège",
            publishedAt = now - 14L * 24 * 60 * 60 * 1000,
            commenceTime = finishedRequest.commenceTime,
        )

        val result = ContextSignalCache.merge(listOf(finishedRequest), emptyMap(), listOf(stored), now)

        assertTrue(result.reports.getValue(finishedRequest.key).signals.isEmpty())
        assertTrue(result.persisted.isEmpty())
    }

    private fun persisted(
        title: String,
        publishedAt: Long,
        category: String = "Encadrement",
        commenceTime: Long = request.commenceTime,
    ) = PersistedContextSignal(
        matchKey = request.key,
        commenceTime = commenceTime,
        teamName = "France",
        title = title,
        publishers = listOf("Média A", "Média B"),
        impact = -0.04,
        category = category,
        publishedAt = publishedAt,
        lastConfirmedAt = publishedAt,
    )
}
