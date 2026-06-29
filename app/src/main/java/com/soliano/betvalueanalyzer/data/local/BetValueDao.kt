package com.soliano.betvalueanalyzer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analyses ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AnalysisRecordEntity>>

    @Insert
    suspend fun insert(record: AnalysisRecordEntity): Long

    @Query("SELECT COUNT(*) FROM analyses")
    suspend fun count(): Int

    @Query("UPDATE analyses SET outcome = :outcome WHERE id = :id")
    suspend fun updateOutcome(id: Long, outcome: String)

    @Query("DELETE FROM analyses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM analyses WHERE competition LIKE 'Démonstration%'")
    suspend fun deleteDemoRecords()
}

@Dao
interface SportDao {
    @Query("SELECT * FROM sports WHERE enabled = 1 ORDER BY category, name")
    fun observeEnabled(): Flow<List<SportEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(sports: List<SportEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sport: SportEntity)

    @Query("SELECT COUNT(*) FROM sports")
    suspend fun count(): Int
}

@Dao
interface PredictionDao {
    @Query("SELECT * FROM predictions ORDER BY commenceTime ASC, confidenceScore DESC")
    fun observeAll(): Flow<List<PredictionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(predictions: List<PredictionEntity>)

    @Query("DELETE FROM predictions")
    suspend fun deleteAll()

    @Query("SELECT * FROM predictions WHERE id LIKE '%:deep:%' AND commenceTime > :now")
    suspend fun getDeepUpcoming(now: Long): List<PredictionEntity>

    @Query("SELECT * FROM predictions WHERE commenceTime > :now ORDER BY commenceTime ASC, confidenceScore DESC")
    suspend fun getUpcoming(now: Long): List<PredictionEntity>

    @Transaction
    suspend fun replaceAll(predictions: List<PredictionEntity>) {
        deleteAll()
        upsertAll(predictions)
    }
}

@Dao
interface UpcomingEventDao {
    @Query("SELECT * FROM upcoming_events ORDER BY commenceTime ASC")
    fun observeAll(): Flow<List<UpcomingEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<UpcomingEventEntity>)

    @Query("DELETE FROM upcoming_events")
    suspend fun deleteAll()

    @Query("UPDATE upcoming_events SET analysisId = :analysisId WHERE id = :eventId")
    suspend fun setAnalysisId(eventId: String, analysisId: String)

    @Query(
        """
        SELECT * FROM upcoming_events
        WHERE (eventType = 'MATCH' AND commenceTime > :now)
           OR (eventType != 'MATCH' AND commenceTime > :lookbackCutoff)
        ORDER BY commenceTime ASC
        """
    )
    suspend fun getActive(now: Long, lookbackCutoff: Long): List<UpcomingEventEntity>

    @Transaction
    suspend fun replaceAll(events: List<UpcomingEventEntity>) {
        deleteAll()
        upsertAll(events)
    }
}

@Dao
interface LiveEventDao {
    @Query("SELECT * FROM live_events ORDER BY isLive DESC, commenceTime ASC")
    fun observeAll(): Flow<List<LiveEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<LiveEventEntity>)

    @Query("DELETE FROM live_events")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(events: List<LiveEventEntity>) {
        deleteAll()
        upsertAll(events)
    }
}
