package com.soliano.betvalueanalyzer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upcoming_events")
data class UpcomingEventEntity(
    @PrimaryKey val id: String,
    val sportKey: String,
    val sportTitle: String,
    val competitionKey: String,
    val competitionName: String,
    val commenceTime: Long,
    val eventName: String,
    val participantA: String,
    val participantB: String,
    val eventType: String,
    val sourceName: String,
    val analysisId: String?,
)
