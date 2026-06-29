package com.soliano.betvalueanalyzer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analyses")
data class AnalysisRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val impliedProbability: Double,
    val estimatedProbability: Double,
    val valueEdge: Double,
    val expectedValue: Double,
    val confidenceScore: Int,
    val confidenceLabel: String,
    val riskLevel: String,
    val valueLevel: String,
    val category: String,
    val positiveArguments: String,
    val negativeArguments: String,
    val explanation: String,
    val suggestedStakePercent: Double,
    val outcome: String = "En attente",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sports")
data class SportEntity(
    @PrimaryKey val name: String,
    val category: String,
    val enabled: Boolean = true,
    val isCustom: Boolean = false,
)

