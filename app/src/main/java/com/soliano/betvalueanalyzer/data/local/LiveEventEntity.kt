package com.soliano.betvalueanalyzer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "live_events")
data class LiveEventEntity(
    @PrimaryKey val id: String,
    val sportKey: String,
    val sportTitle: String,
    val competitionName: String,
    val commenceTime: Long,
    val eventName: String,
    val homeName: String,
    val awayName: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val statusState: String,
    val statusDescription: String,
    val displayClock: String,
    val period: Int?,
    val isLive: Boolean,
    val sourceName: String,
    val sourceDetails: String,
    val lastUpdate: Long,
    val statSummary: String,
    val scenarios: String,
)
