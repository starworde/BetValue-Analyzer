package com.soliano.betvalueanalyzer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisEngineTest {

    @Test
    fun `implicit probability is inverse of decimal odds`() {
        val result = AnalysisEngine.analyze(baseInput(odds = 2.0))
        assertEquals(0.5, result.impliedProbability, 0.0001)
    }

    @Test
    fun `strong form creates a higher estimated probability`() {
        val strong = AnalysisEngine.analyze(baseInput(formA = 88, formB = 35, dataQuality = 90))
        val balanced = AnalysisEngine.analyze(baseInput(formA = 55, formB = 55, dataQuality = 90))
        assertTrue(strong.estimatedProbability > balanced.estimatedProbability)
        assertTrue(strong.confidenceScore > balanced.confidenceScore)
    }

    @Test
    fun `uncertainty pulls estimate toward fifty percent`() {
        val certain = AnalysisEngine.analyze(baseInput(formA = 85, formB = 35, uncertainty = 5))
        val uncertain = AnalysisEngine.analyze(baseInput(formA = 85, formB = 35, uncertainty = 95))
        assertTrue(certain.estimatedProbability > uncertain.estimatedProbability)
    }

    @Test
    fun `suggested stake remains responsible`() {
        val result = AnalysisEngine.analyze(baseInput(odds = 2.2, formA = 95, formB = 20, dataQuality = 95, uncertainty = 5))
        assertTrue(result.suggestedStakePercent in 0.0..2.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid odds are rejected`() {
        AnalysisEngine.analyze(baseInput(odds = 1.0))
    }

    private fun baseInput(
        odds: Double = 2.0,
        formA: Int = 70,
        formB: Int = 50,
        dataQuality: Int = 80,
        uncertainty: Int = 20,
    ) = ManualAnalysisInput(
        sport = "Football",
        competition = "Test",
        participantA = "A",
        participantB = "B",
        eventDate = "Demain",
        market = "Vainqueur",
        selection = "A gagne",
        odds = odds,
        formA = formA,
        formB = formB,
        dataQuality = dataQuality,
        uncertainty = uncertainty,
        homeAdvantage = true,
        contextNote = "",
    )
}

