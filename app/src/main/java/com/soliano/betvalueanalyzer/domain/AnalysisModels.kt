package com.soliano.betvalueanalyzer.domain

data class ManualAnalysisInput(
    val sport: String,
    val competition: String,
    val participantA: String,
    val participantB: String,
    val eventDate: String,
    val market: String,
    val selection: String,
    val odds: Double,
    val formA: Int,
    val formB: Int,
    val dataQuality: Int,
    val uncertainty: Int,
    val homeAdvantage: Boolean,
    val contextNote: String,
)

data class AnalysisResult(
    val impliedProbability: Double,
    val estimatedProbability: Double,
    val valueEdge: Double,
    val expectedValue: Double,
    val confidenceScore: Int,
    val confidenceLabel: String,
    val riskLevel: String,
    val valueLevel: String,
    val category: String,
    val positiveArguments: List<String>,
    val negativeArguments: List<String>,
    val explanation: String,
    val suggestedStakePercent: Double,
)

data class SportProfile(
    val group: String,
    val formImpact: Double,
    val homeImpact: Double,
    val volatility: Double,
    val keySignal: String,
)

fun confidenceLabel(score: Int): String = when {
    score >= 90 -> "Très forte"
    score >= 75 -> "Élevée"
    score >= 60 -> "Moyenne"
    score >= 45 -> "Faible"
    else -> "Déconseillé"
}

