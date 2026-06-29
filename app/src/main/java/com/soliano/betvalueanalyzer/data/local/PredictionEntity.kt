package com.soliano.betvalueanalyzer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "predictions")
data class PredictionEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val sportKey: String,
    val sportTitle: String,
    val competitionName: String,
    val commenceTime: Long,
    val homeTeam: String,
    val awayTeam: String,
    val market: String,
    val selection: String,
    val betclicOdds: Double,
    val impliedProbability: Double,
    val consensusProbability: Double,
    val valueEdge: Double,
    val expectedValue: Double,
    val confidenceScore: Int,
    val riskLevel: String,
    val category: String,
    val bookmakerCount: Int,
    val sourceName: String,
    val sourceLastUpdate: Long,
    val explanation: String,
    val positiveArguments: String,
    val negativeArguments: String,
    val expectedScore: String,
    val statSummary: String,
    val scenarios: String,
    val homeLineupStatus: String,
    val homeLineup: String,
    val awayLineupStatus: String,
    val awayLineup: String,
    val playerScenarios: String,
    val sourceDetails: String,
    val contextInsights: String,
    val sourceAgreement: Int,
)
