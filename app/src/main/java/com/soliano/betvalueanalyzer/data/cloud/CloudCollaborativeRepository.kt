package com.soliano.betvalueanalyzer.data.cloud

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.soliano.betvalueanalyzer.data.PreferencesRepository
import com.soliano.betvalueanalyzer.data.local.PredictionDao
import com.soliano.betvalueanalyzer.data.local.UpcomingEventDao
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

private const val CLOUD_WRITE_INTERVAL_MS = 5 * 60 * 1000L
private const val CLOUD_MAX_FETCH_PER_SYNC = 120L
private const val CLOUD_MAX_KNOWN_DOCS_PER_SYNC = 5_000L
private const val CLOUD_FIRESTORE_BATCH_LIMIT = 450

class CloudCollaborativeRepository(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val predictionDao: PredictionDao,
    private val upcomingEventDao: UpcomingEventDao,
    private val remoteDataSource: CloudCollaborativeRemoteDataSource = FirebaseCloudCollaborativeRemoteDataSource(context),
) {
    suspend fun sync(
        appVersion: String,
        forceUpload: Boolean = false,
        publishLocal: Boolean = true,
        fetchCloud: Boolean = true,
    ): CloudSyncReport {
        val now = System.currentTimeMillis()
        val settings = preferencesRepository.settings.first()
        if (!settings.cloudCollaborativeEnabled) {
            return CloudSyncReport(enabled = false, firebaseAvailable = false, lastSyncEpoch = now)
        }

        return runCatching {
            val firebaseAvailable = remoteDataSource.isAvailable()
            if (!firebaseAvailable) {
                val report = CloudSyncReport(
                    enabled = true,
                    firebaseAvailable = false,
                    lastSyncEpoch = now,
                    errorMessage = "Firebase non configuré ou indisponible",
                )
                preferencesRepository.updateCloudMetadata(report)
                return@runCatching report
            }

            val deviceId = if (publishLocal || fetchCloud) remoteDataSource.anonymousDeviceId() else ""
            val localPredictions = predictionDao.getUpcoming(now)
            val localEvents = upcomingEventDao.getActive(now, now - 48 * 60 * 60 * 1000L)
            var attemptedUpload = 0
            var uploaded = 0
            var lastUpload = settings.lastCloudUploadEpoch
            val canUpload = forceUpload || now - settings.lastCloudUploadEpoch >= CLOUD_WRITE_INTERVAL_MS
            if (publishLocal && canUpload) {
                val predictionPayload = localPredictions
                    .asSequence()
                    .mapNotNull { it.toCloudSharedResult(appVersion, deviceId, now) }
                    .toList()
                val predictionDocumentIds = predictionPayload
                    .asSequence()
                    .map { cloudDocumentIdFor(it.eventId) }
                    .toSet()
                val existingDocumentIds = remoteDataSource.fetchKnownDocumentIds(now, CLOUD_MAX_KNOWN_DOCS_PER_SYNC)
                val calendarPayload = localEvents
                    .asSequence()
                    .mapNotNull { it.toCloudSharedCalendarEvent(appVersion, deviceId, now) }
                    .filter { event ->
                        val documentId = cloudDocumentIdFor(event.eventId)
                        documentId !in predictionDocumentIds && documentId !in existingDocumentIds
                    }
                    .toList()
                attemptedUpload = predictionPayload.size + calendarPayload.size
                if (predictionPayload.isNotEmpty() || calendarPayload.isNotEmpty()) {
                    uploaded = remoteDataSource.publish(predictionPayload) +
                        remoteDataSource.publishCalendarEvents(calendarPayload)
                    lastUpload = now
                }
            }

            var fetchedCount = 0
            var mergedCount = 0
            var rejectedCount = 0
            var lastRead = settings.lastCloudReadEpoch
            if (fetchCloud) {
                val cloudResults = remoteDataSource.fetchRecent(now, CLOUD_MAX_FETCH_PER_SYNC)
                fetchedCount = cloudResults.size
                val merge = mergeCloudResults(localPredictions, cloudResults, now)
                rejectedCount = merge.rejectedCloudCount
                mergedCount = merge.predictionsToUpsert.size
                if (merge.predictionsToUpsert.isNotEmpty()) {
                    predictionDao.upsertAll(merge.predictionsToUpsert)
                }
                lastRead = now
            }

            CloudSyncReport(
                enabled = true,
                firebaseAvailable = true,
                attemptedUploadCount = attemptedUpload,
                uploadedCount = uploaded,
                fetchedCount = fetchedCount,
                mergedCount = mergedCount,
                rejectedCount = rejectedCount,
                lastSyncEpoch = now,
                lastUploadEpoch = lastUpload,
                lastReadEpoch = lastRead,
            ).also { preferencesRepository.updateCloudMetadata(it) }
        }.getOrElse { error ->
            val report = CloudSyncReport(
                enabled = true,
                firebaseAvailable = false,
                lastSyncEpoch = now,
                errorMessage = cloudCollaborativeErrorMessage(error),
            )
            preferencesRepository.updateCloudMetadata(report)
            report
        }
    }

    suspend fun exportDiagnostic(appVersion: String): CloudSyncReport {
        val now = System.currentTimeMillis()
        val settings = preferencesRepository.settings.first()
        val localCount = runCatching { predictionDao.getUpcoming(now).size }.getOrDefault(0)
        val firebaseAvailable = runCatching { remoteDataSource.isAvailable() }.getOrDefault(false)
        val file = File(context.cacheDir, "cloud_collaboratif_diagnostic.txt")
        val text = buildString {
            appendLine("BetValue Analyzer - diagnostic cloud collaboratif")
            appendLine("generatedAt=$now")
            appendLine("appVersion=$appVersion")
            appendLine("enabled=${settings.cloudCollaborativeEnabled}")
            appendLine("firebaseAvailable=$firebaseAvailable")
            appendLine("localUpcomingPredictions=$localCount")
            appendLine("lastCloudSync=${settings.lastCloudSyncEpoch}")
            appendLine("lastCloudUpload=${settings.lastCloudUploadEpoch}")
            appendLine("lastCloudRead=${settings.lastCloudReadEpoch}")
            appendLine("lastCloudError=${settings.lastCloudError}")
            appendLine("lastCloudFetchedCount=${settings.lastCloudFetchedCount}")
            appendLine("privacy=aucun favori, historique privé, log sensible ou donnée personnelle n'est envoyé")
        }
        runCatching { file.writeText(text) }
        val report = CloudSyncReport(
            enabled = settings.cloudCollaborativeEnabled,
            firebaseAvailable = firebaseAvailable,
            fetchedCount = settings.lastCloudFetchedCount,
            lastSyncEpoch = now,
            lastUploadEpoch = settings.lastCloudUploadEpoch,
            lastReadEpoch = settings.lastCloudReadEpoch,
            errorMessage = settings.lastCloudError,
            diagnosticPath = file.absolutePath,
        )
        preferencesRepository.updateCloudMetadata(report)
        return report
    }
}

interface CloudCollaborativeRemoteDataSource {
    fun isAvailable(): Boolean
    fun anonymousDeviceId(): String
    fun publish(results: List<CloudSharedResult>): Int
    fun publishCalendarEvents(events: List<CloudSharedCalendarEvent>): Int
    fun fetchRecent(now: Long, limit: Long): List<CloudSharedResult>
    fun fetchKnownDocumentIds(now: Long, limit: Long): Set<String>
}

class FirebaseCloudCollaborativeRemoteDataSource(
    context: Context,
) : CloudCollaborativeRemoteDataSource {
    private val appContext = context.applicationContext

    override fun isAvailable(): Boolean = firebaseAppOrNull() != null

    override fun anonymousDeviceId(): String {
        val app = firebaseAppOrNull() ?: throw IllegalStateException("Firebase non configuré")
        return runCatching {
            val auth = FirebaseAuth.getInstance(app)
            if (auth.currentUser == null) {
                Tasks.await(auth.signInAnonymously(), 8, TimeUnit.SECONDS)
            }
            auth.currentUser?.uid?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Aucun utilisateur anonyme Firebase")
        }.getOrElse { error ->
            throw IllegalStateException("Auth anonyme Firebase indisponible : ${error.message}", error)
        }
    }

    override fun publish(results: List<CloudSharedResult>): Int {
        if (results.isEmpty()) return 0
        val firestore = firestoreOrNull() ?: return 0
        val now = System.currentTimeMillis()
        val collection = firestore.collection("shared_results")
        val coherent = results.filter { it.isCoherent(now) }
        val publishable = coherent.filter { result ->
            val document = collection.document(cloudDocumentIdFor(result.eventId))
            val existing = runCatching {
                Tasks.await(document.get(), 8, TimeUnit.SECONDS).data
                    ?.let(::cloudSharedResultFromMap)
            }.getOrNull()
            existing == null || !existing.isCoherent(now) || existing.updatedAt <= result.updatedAt
        }
        if (publishable.isEmpty()) return 0
        publishable.chunked(CLOUD_FIRESTORE_BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { result ->
                batch.set(
                    collection.document(cloudDocumentIdFor(result.eventId)),
                    result.toFirestoreMap(),
                    SetOptions.merge(),
                )
            }
            Tasks.await(batch.commit(), 15, TimeUnit.SECONDS)
        }
        return publishable.size
    }

    override fun publishCalendarEvents(events: List<CloudSharedCalendarEvent>): Int {
        if (events.isEmpty()) return 0
        val firestore = firestoreOrNull() ?: return 0
        val now = System.currentTimeMillis()
        val collection = firestore.collection("shared_results")
        val publishable = events
            .filter { it.isCoherent(now) }
            .distinctBy { cloudDocumentIdFor(it.eventId) }
        if (publishable.isEmpty()) return 0
        publishable.chunked(CLOUD_FIRESTORE_BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { event ->
                batch.set(
                    collection.document(cloudDocumentIdFor(event.eventId)),
                    event.toFirestoreMap(),
                    SetOptions.merge(),
                )
            }
            Tasks.await(batch.commit(), 15, TimeUnit.SECONDS)
        }
        return publishable.size
    }

    override fun fetchRecent(now: Long, limit: Long): List<CloudSharedResult> {
        val firestore = firestoreOrNull() ?: return emptyList()
        val perCollectionLimit = (limit / 2).coerceAtLeast(40L)
        return (
            fetchRecentFromCollection(firestore, "cloud_results", now, perCollectionLimit) +
                fetchRecentFromCollection(firestore, "shared_results", now, perCollectionLimit)
            )
            .filter { it.isCoherent(now) }
            .sortedByDescending { it.updatedAt }
            .distinctBy { cloudDocumentIdFor(it.eventId) }
            .take(limit.toInt())
    }

    private fun fetchRecentFromCollection(
        firestore: FirebaseFirestore,
        collectionName: String,
        now: Long,
        limit: Long,
    ): List<CloudSharedResult> {
        val snapshot = Tasks.await(
            firestore.collection(collectionName)
                .whereGreaterThan("expiresAt", now)
                .limit(limit)
                .get(),
            10,
            TimeUnit.SECONDS,
        )
        return snapshot.documents.mapNotNull { document ->
            cloudSharedResultFromMap(document.data.orEmpty())
        }
    }

    override fun fetchKnownDocumentIds(now: Long, limit: Long): Set<String> {
        val firestore = firestoreOrNull() ?: return emptySet()
        val snapshot = Tasks.await(
            firestore.collection("shared_results")
                .whereGreaterThan("expiresAt", now)
                .limit(limit)
                .get(),
            12,
            TimeUnit.SECONDS,
        )
        return snapshot.documents.map { it.id }.toSet()
    }

    private fun firestoreOrNull(): FirebaseFirestore? {
        val app = firebaseAppOrNull() ?: return null
        return runCatching { FirebaseFirestore.getInstance(app) }.getOrNull()
    }

    private fun firebaseAppOrNull(): FirebaseApp? = runCatching {
        FirebaseApp.getApps(appContext).firstOrNull() ?: FirebaseApp.initializeApp(appContext)
    }.getOrNull()
}

internal fun cloudCollaborativeErrorMessage(error: Throwable): String {
    val raw = generateSequence(error) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
        .trim()
    val normalized = raw.lowercase(Locale.US)
    return when {
        normalized.contains("cloud firestore api has not been used") ||
            (normalized.contains("firestore") && normalized.contains("disabled")) ->
            "Cloud Firestore n'est pas active dans Firebase Console. Active Firestore Database puis deploie les regles."

        normalized.contains("permission_denied") ||
            normalized.contains("missing or insufficient permissions") ->
            "Acces Firestore refuse. Verifie les regles Firestore et l'auth anonyme Firebase."

        normalized.contains("configuration_not_found") ||
            normalized.contains("anonymous") && normalized.contains("disabled") ->
            "Auth anonyme Firebase non activee. Active Authentication > Anonymous dans Firebase Console."

        normalized.contains("network") ||
            normalized.contains("timeout") ||
            normalized.contains("timed out") ->
            "Cloud temporairement indisponible. L'application continue avec les donnees locales."

        else -> raw.take(180).ifBlank { "Cloud indisponible" }
    }
}
