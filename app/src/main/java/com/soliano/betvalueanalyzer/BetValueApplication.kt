package com.soliano.betvalueanalyzer

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.soliano.betvalueanalyzer.data.AnalysisRepository
import com.soliano.betvalueanalyzer.data.PreferencesRepository
import com.soliano.betvalueanalyzer.data.SportsAnalysisRepository
import com.soliano.betvalueanalyzer.data.cloud.CloudCollaborativeRepository
import com.soliano.betvalueanalyzer.data.local.BetValueDatabase
import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import com.soliano.betvalueanalyzer.sync.SportsSyncWorker

class BetValueApplication : Application() {
    private val database by lazy { BetValueDatabase.getInstance(this) }

    val analysisRepository by lazy {
        AnalysisRepository(database.analysisDao(), database.sportDao())
    }

    val preferencesRepository by lazy { PreferencesRepository(this) }

    val sportsAnalysisRepository by lazy {
        SportsAnalysisRepository(
            database.predictionDao(),
            database.upcomingEventDao(),
            database.liveEventDao(),
            preferencesRepository,
            PublicSportsApiService.create(),
            preferencesRepository,
        )
    }

    val cloudCollaborativeRepository by lazy {
        CloudCollaborativeRepository(
            context = this,
            preferencesRepository = preferencesRepository,
            predictionDao = database.predictionDao(),
            upcomingEventDao = database.upcomingEventDao(),
        )
    }

    override fun onCreate() {
        super.onCreate()
        initializeFirebaseAppCheckIfConfigured()
        SportsSyncWorker.schedule(this)
    }

    private fun initializeFirebaseAppCheckIfConfigured() {
        runCatching {
            val app = FirebaseApp.getApps(this).firstOrNull() ?: FirebaseApp.initializeApp(this) ?: return
            FirebaseAppCheck.getInstance(app)
                .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }
    }
}
