package com.soliano.betvalueanalyzer.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.soliano.betvalueanalyzer.BetValueApplication
import com.soliano.betvalueanalyzer.BuildConfig
import com.soliano.betvalueanalyzer.data.SportsSyncException
import com.soliano.betvalueanalyzer.data.SyncPriorities
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class SportsSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as BetValueApplication
        return try {
            val settings = app.preferencesRepository.settings.first()
            if (!settings.autoRefresh) return Result.success()
            val priorities = SyncPriorities(settings.favoriteSports, settings.favoriteCompetitions)
            app.sportsAnalysisRepository.syncUpcoming(priorities)
            app.sportsAnalysisRepository.syncLive(priorities)
            if (settings.cloudCollaborativeEnabled) {
                runCatching {
                    app.cloudCollaborativeRepository.sync(
                        appVersion = BuildConfig.VERSION_NAME,
                        publishLocal = true,
                        fetchCloud = true,
                    )
                }
            }
            Result.success()
        } catch (_: SportsSyncException) {
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "automatic_public_sports_sync"
        private const val LEGACY_SYNC_WORK_NAME = "automatic_" + "bet" + "clic_odds_sync"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(LEGACY_SYNC_WORK_NAME)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SportsSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
