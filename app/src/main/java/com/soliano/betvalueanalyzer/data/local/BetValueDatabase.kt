package com.soliano.betvalueanalyzer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AnalysisRecordEntity::class, SportEntity::class, PredictionEntity::class, UpcomingEventEntity::class, LiveEventEntity::class],
    version = 10,
    exportSchema = true,
)
abstract class BetValueDatabase : RoomDatabase() {
    abstract fun analysisDao(): AnalysisDao
    abstract fun sportDao(): SportDao
    abstract fun predictionDao(): PredictionDao
    abstract fun upcomingEventDao(): UpcomingEventDao
    abstract fun liveEventDao(): LiveEventDao

    companion object {
        @Volatile private var instance: BetValueDatabase? = null

        fun getInstance(context: Context): BetValueDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                BetValueDatabase::class.java,
                "betvalue.db",
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
            ).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS predictions (
                        id TEXT NOT NULL PRIMARY KEY,
                        eventId TEXT NOT NULL,
                        sportKey TEXT NOT NULL,
                        sportTitle TEXT NOT NULL,
                        commenceTime INTEGER NOT NULL,
                        homeTeam TEXT NOT NULL,
                        awayTeam TEXT NOT NULL,
                        market TEXT NOT NULL,
                        selection TEXT NOT NULL,
                        betclicOdds REAL NOT NULL,
                        impliedProbability REAL NOT NULL,
                        consensusProbability REAL NOT NULL,
                        valueEdge REAL NOT NULL,
                        expectedValue REAL NOT NULL,
                        confidenceScore INTEGER NOT NULL,
                        riskLevel TEXT NOT NULL,
                        category TEXT NOT NULL,
                        bookmakerCount INTEGER NOT NULL,
                        sourceLastUpdate INTEGER NOT NULL,
                        explanation TEXT NOT NULL,
                        positiveArguments TEXT NOT NULL,
                        negativeArguments TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE predictions ADD COLUMN sourceName TEXT NOT NULL DEFAULT 'Source publique'"
                )
                db.execSQL("DELETE FROM predictions")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE predictions ADD COLUMN competitionName TEXT NOT NULL DEFAULT 'Compétition'"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS upcoming_events (
                        id TEXT NOT NULL PRIMARY KEY,
                        sportKey TEXT NOT NULL,
                        sportTitle TEXT NOT NULL,
                        competitionKey TEXT NOT NULL,
                        competitionName TEXT NOT NULL,
                        commenceTime INTEGER NOT NULL,
                        eventName TEXT NOT NULL,
                        participantA TEXT NOT NULL,
                        participantB TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        sourceName TEXT NOT NULL,
                        analysisId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("DELETE FROM predictions")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE predictions ADD COLUMN expectedScore TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE predictions ADD COLUMN statSummary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE predictions ADD COLUMN scenarios TEXT NOT NULL DEFAULT ''")
                db.execSQL("DELETE FROM predictions")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE predictions ADD COLUMN homeLineupStatus TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE predictions ADD COLUMN homeLineup TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE predictions ADD COLUMN awayLineupStatus TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE predictions ADD COLUMN awayLineup TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE predictions ADD COLUMN playerScenarios TEXT NOT NULL DEFAULT ''")
                db.execSQL("DELETE FROM predictions")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE predictions ADD COLUMN sourceDetails TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE predictions ADD COLUMN contextInsights TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE predictions ADD COLUMN sourceAgreement INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DELETE FROM predictions")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS live_events (
                        id TEXT NOT NULL PRIMARY KEY,
                        sportKey TEXT NOT NULL,
                        sportTitle TEXT NOT NULL,
                        competitionName TEXT NOT NULL,
                        commenceTime INTEGER NOT NULL,
                        eventName TEXT NOT NULL,
                        homeName TEXT NOT NULL,
                        awayName TEXT NOT NULL,
                        homeScore INTEGER,
                        awayScore INTEGER,
                        statusState TEXT NOT NULL,
                        statusDescription TEXT NOT NULL,
                        displayClock TEXT NOT NULL,
                        period INTEGER,
                        isLive INTEGER NOT NULL,
                        sourceName TEXT NOT NULL,
                        sourceDetails TEXT NOT NULL,
                        lastUpdate INTEGER NOT NULL,
                        statSummary TEXT NOT NULL,
                        scenarios TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE predictions RENAME COLUMN betclicOdds TO referenceOdds")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE predictions ADD COLUMN aiAnalysis TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE predictions ADD COLUMN aiDiagnostic TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE predictions ADD COLUMN aiGeneratedAt INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
