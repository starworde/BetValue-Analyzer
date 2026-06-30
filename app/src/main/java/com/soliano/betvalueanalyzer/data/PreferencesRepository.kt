package com.soliano.betvalueanalyzer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.soliano.betvalueanalyzer.data.cloud.CloudSyncReport
import com.soliano.betvalueanalyzer.domain.RemovedSports
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "user_settings")

data class UserSettings(
    val ageConfirmed: Boolean = false,
    val analysisOnly: Boolean = false,
    val pauseReminders: Boolean = true,
    val autoRefresh: Boolean = true,
    val lastSyncEpoch: Long = 0L,
    val favoriteSports: Set<String> = emptySet(),
    val favoriteCompetitions: Set<String> = emptySet(),
    val appLanguage: String = "fr",
    val cloudCollaborativeEnabled: Boolean = true,
    val lastCloudSyncEpoch: Long = 0L,
    val lastCloudUploadEpoch: Long = 0L,
    val lastCloudReadEpoch: Long = 0L,
    val lastCloudError: String = "",
    val lastCloudFetchedCount: Int = 0,
    val cloudJobStatus: String = "",
    val cloudJobFinishedEpoch: Long = 0L,
    val cloudJobUpdatedEpoch: Long = 0L,
    val cloudJobEventsFound: Int = 0,
    val cloudJobResultsPrepared: Int = 0,
    val cloudJobResultsWritten: Int = 0,
    val cloudJobRemovedSportsDeleted: Int = 0,
    val cloudJobSourcesChecked: Int = 0,
    val cloudJobSourceErrors: Int = 0,
    val cloudJobFirestoreError: String = "",
    val cloudJobFirestoreCleanupError: String = "",
    val cloudJobError: String = "",
)

interface SyncMetadataStore {
    suspend fun updateSyncMetadata(timestamp: Long)
}

class PreferencesRepository(private val context: Context) : SyncMetadataStore, ContextSignalStore {
    private val gson = Gson()

    private object Keys {
        val ageConfirmed = booleanPreferencesKey("age_confirmed")
        val analysisOnly = booleanPreferencesKey("analysis_only")
        val pauseReminders = booleanPreferencesKey("pause_reminders")
        val autoRefresh = booleanPreferencesKey("auto_refresh")
        val lastSyncEpoch = longPreferencesKey("last_sync_epoch")
        val favoriteSports = stringSetPreferencesKey("favorite_sports")
        val favoriteCompetitions = stringSetPreferencesKey("favorite_competitions")
        val appLanguage = stringPreferencesKey("app_language")
        val contextSignals = stringPreferencesKey("persistent_match_context_v1")
        val cloudCollaborativeEnabled = booleanPreferencesKey("cloud_collaborative_enabled")
        val lastCloudSyncEpoch = longPreferencesKey("last_cloud_sync_epoch")
        val lastCloudUploadEpoch = longPreferencesKey("last_cloud_upload_epoch")
        val lastCloudReadEpoch = longPreferencesKey("last_cloud_read_epoch")
        val lastCloudError = stringPreferencesKey("last_cloud_error")
        val lastCloudFetchedCount = longPreferencesKey("last_cloud_fetched_count")
        val cloudJobStatus = stringPreferencesKey("cloud_job_status")
        val cloudJobFinishedEpoch = longPreferencesKey("cloud_job_finished_epoch")
        val cloudJobUpdatedEpoch = longPreferencesKey("cloud_job_updated_epoch")
        val cloudJobEventsFound = longPreferencesKey("cloud_job_events_found")
        val cloudJobResultsPrepared = longPreferencesKey("cloud_job_results_prepared")
        val cloudJobResultsWritten = longPreferencesKey("cloud_job_results_written")
        val cloudJobRemovedSportsDeleted = longPreferencesKey("cloud_job_removed_sports_deleted")
        val cloudJobSourcesChecked = longPreferencesKey("cloud_job_sources_checked")
        val cloudJobSourceErrors = longPreferencesKey("cloud_job_source_errors")
        val cloudJobFirestoreError = stringPreferencesKey("cloud_job_firestore_error")
        val cloudJobFirestoreCleanupError = stringPreferencesKey("cloud_job_firestore_cleanup_error")
        val cloudJobError = stringPreferencesKey("cloud_job_error")
    }

    val settings: Flow<UserSettings> = context.dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            UserSettings(
                ageConfirmed = preferences[Keys.ageConfirmed] ?: false,
                analysisOnly = preferences[Keys.analysisOnly] ?: false,
                pauseReminders = preferences[Keys.pauseReminders] ?: true,
                autoRefresh = preferences[Keys.autoRefresh] ?: true,
                lastSyncEpoch = preferences[Keys.lastSyncEpoch] ?: 0L,
                favoriteSports = preferences[Keys.favoriteSports].orEmpty()
                    .filterNot(RemovedSports::isRemovedSportKey)
                    .toSet(),
                favoriteCompetitions = preferences[Keys.favoriteCompetitions].orEmpty()
                    .filterNot(RemovedSports::isRemovedCompetitionKey)
                    .toSet(),
                appLanguage = preferences[Keys.appLanguage] ?: "fr",
                cloudCollaborativeEnabled = preferences[Keys.cloudCollaborativeEnabled] ?: true,
                lastCloudSyncEpoch = preferences[Keys.lastCloudSyncEpoch] ?: 0L,
                lastCloudUploadEpoch = preferences[Keys.lastCloudUploadEpoch] ?: 0L,
                lastCloudReadEpoch = preferences[Keys.lastCloudReadEpoch] ?: 0L,
                lastCloudError = preferences[Keys.lastCloudError].orEmpty(),
                lastCloudFetchedCount = (preferences[Keys.lastCloudFetchedCount] ?: 0L).toInt(),
                cloudJobStatus = preferences[Keys.cloudJobStatus].orEmpty(),
                cloudJobFinishedEpoch = preferences[Keys.cloudJobFinishedEpoch] ?: 0L,
                cloudJobUpdatedEpoch = preferences[Keys.cloudJobUpdatedEpoch] ?: 0L,
                cloudJobEventsFound = (preferences[Keys.cloudJobEventsFound] ?: 0L).toInt(),
                cloudJobResultsPrepared = (preferences[Keys.cloudJobResultsPrepared] ?: 0L).toInt(),
                cloudJobResultsWritten = (preferences[Keys.cloudJobResultsWritten] ?: 0L).toInt(),
                cloudJobRemovedSportsDeleted = (preferences[Keys.cloudJobRemovedSportsDeleted] ?: 0L).toInt(),
                cloudJobSourcesChecked = (preferences[Keys.cloudJobSourcesChecked] ?: 0L).toInt(),
                cloudJobSourceErrors = (preferences[Keys.cloudJobSourceErrors] ?: 0L).toInt(),
                cloudJobFirestoreError = preferences[Keys.cloudJobFirestoreError].orEmpty(),
                cloudJobFirestoreCleanupError = preferences[Keys.cloudJobFirestoreCleanupError].orEmpty(),
                cloudJobError = preferences[Keys.cloudJobError].orEmpty(),
            )
        }

    suspend fun confirmAge() = context.dataStore.edit { it[Keys.ageConfirmed] = true }
    suspend fun setAnalysisOnly(value: Boolean) = context.dataStore.edit { it[Keys.analysisOnly] = value }
    suspend fun setPauseReminders(value: Boolean) = context.dataStore.edit { it[Keys.pauseReminders] = value }
    suspend fun setAutoRefresh(value: Boolean) = context.dataStore.edit { it[Keys.autoRefresh] = value }
    suspend fun setAppLanguage(value: String) = context.dataStore.edit { preferences ->
        preferences[Keys.appLanguage] = value.takeIf { it in setOf("fr", "en", "es", "de") } ?: "fr"
    }
    override suspend fun updateSyncMetadata(timestamp: Long) {
        context.dataStore.edit { it[Keys.lastSyncEpoch] = timestamp }
    }

    suspend fun setCloudCollaborativeEnabled(value: Boolean) = context.dataStore.edit { preferences ->
        preferences[Keys.cloudCollaborativeEnabled] = value
    }

    suspend fun updateCloudMetadata(report: CloudSyncReport) {
        context.dataStore.edit { preferences ->
            if (report.lastSyncEpoch > 0L) preferences[Keys.lastCloudSyncEpoch] = report.lastSyncEpoch
            if (report.lastUploadEpoch > 0L) preferences[Keys.lastCloudUploadEpoch] = report.lastUploadEpoch
            if (report.lastReadEpoch > 0L) preferences[Keys.lastCloudReadEpoch] = report.lastReadEpoch
            preferences[Keys.lastCloudFetchedCount] = report.fetchedCount.toLong()
            preferences[Keys.lastCloudError] = report.errorMessage
            val diagnostic = report.jobDiagnostic
            preferences[Keys.cloudJobStatus] = diagnostic.status
            preferences[Keys.cloudJobFinishedEpoch] = diagnostic.finishedAt
            preferences[Keys.cloudJobUpdatedEpoch] = diagnostic.updatedAt
            preferences[Keys.cloudJobEventsFound] = diagnostic.eventsFound.toLong()
            preferences[Keys.cloudJobResultsPrepared] = diagnostic.resultsPrepared.toLong()
            preferences[Keys.cloudJobResultsWritten] = diagnostic.resultsWritten.toLong()
            preferences[Keys.cloudJobRemovedSportsDeleted] = diagnostic.removedSportDocumentsDeleted.toLong()
            preferences[Keys.cloudJobSourcesChecked] = diagnostic.sourcesChecked.toLong()
            preferences[Keys.cloudJobSourceErrors] = diagnostic.sourceErrorsCount.toLong()
            preferences[Keys.cloudJobFirestoreError] = diagnostic.firestoreError
            preferences[Keys.cloudJobFirestoreCleanupError] = diagnostic.firestoreCleanupError
            preferences[Keys.cloudJobError] = diagnostic.error
        }
    }

    override suspend fun loadContextSignals(): List<PersistedContextSignal> {
        val json = runCatching { context.dataStore.data.first()[Keys.contextSignals] }.getOrNull() ?: return emptyList()
        val type = object : TypeToken<List<PersistedContextSignal>>() {}.type
        return runCatching { gson.fromJson<List<PersistedContextSignal>>(json, type).orEmpty() }.getOrDefault(emptyList())
    }

    override suspend fun saveContextSignals(signals: List<PersistedContextSignal>) {
        runCatching {
            context.dataStore.edit { preferences ->
                preferences[Keys.contextSignals] = gson.toJson(signals)
            }
        }
    }

    suspend fun setFavoriteSport(key: String, favorite: Boolean) = context.dataStore.edit { preferences ->
        val values = preferences[Keys.favoriteSports].orEmpty().toMutableSet()
        if (favorite && !RemovedSports.isRemovedSportKey(key)) values.add(key) else values.remove(key)
        values.removeAll { RemovedSports.isRemovedSportKey(it) }
        preferences[Keys.favoriteSports] = values
    }

    suspend fun setFavoriteCompetition(key: String, favorite: Boolean) = context.dataStore.edit { preferences ->
        val values = preferences[Keys.favoriteCompetitions].orEmpty().toMutableSet()
        if (favorite && !RemovedSports.isRemovedCompetitionKey(key)) values.add(key) else values.remove(key)
        values.removeAll { RemovedSports.isRemovedCompetitionKey(it) }
        preferences[Keys.favoriteCompetitions] = values
    }

    suspend fun purgeRemovedSports() = context.dataStore.edit { preferences ->
        preferences[Keys.favoriteSports] = preferences[Keys.favoriteSports].orEmpty()
            .filterNot(RemovedSports::isRemovedSportKey)
            .toSet()
        preferences[Keys.favoriteCompetitions] = preferences[Keys.favoriteCompetitions].orEmpty()
            .filterNot(RemovedSports::isRemovedCompetitionKey)
            .toSet()
    }
}
