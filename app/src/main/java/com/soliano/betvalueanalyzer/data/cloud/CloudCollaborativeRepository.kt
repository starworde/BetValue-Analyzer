package com.soliano.betvalueanalyzer.data.cloud

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.soliano.betvalueanalyzer.data.PreferencesRepository
import com.soliano.betvalueanalyzer.data.local.PredictionDao
import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.data.local.UpcomingEventDao
import com.soliano.betvalueanalyzer.data.local.UpcomingEventEntity
import com.soliano.betvalueanalyzer.domain.RemovedSports
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

private const val CLOUD_WRITE_INTERVAL_MS = 5 * 60 * 1000L
private const val CLOUD_MAX_FETCH_PER_SYNC = 1_200L
private const val CLOUD_MAX_KNOWN_DOCS_PER_SYNC = 5_000L
private const val CLOUD_FIRESTORE_BATCH_LIMIT = 450

class CloudCollaborativeRepository(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val predictionDao: PredictionDao,
    private val upcomingEventDao: UpcomingEventDao,
    private val remoteDataSource: CloudCollaborativeRemoteDataSource = FirebaseCloudCollaborativeRemoteDataSource(context),
) {
    suspend fun preparePredictionForDetail(
        appVersion: String,
        prediction: PredictionEntity,
    ): CloudPreparedPrediction {
        val hydrated = hydratePredictionWithCloudAi(prediction)
        val request = requestAiAnalysisForPrediction(appVersion, hydrated)
        return CloudPreparedPrediction(
            prediction = if (hydrated.aiAnalysis.hasValidCloudAiAnalysis()) {
                hydrated
            } else {
                hydrated.withCloudAiRequestDiagnostic(request)
            },
            request = request,
        )
    }

    suspend fun requestAiAnalysisForEvent(
        appVersion: String,
        event: UpcomingEventEntity,
    ): CloudAiRequestOutcome {
        val now = System.currentTimeMillis()
        val settings = preferencesRepository.settings.first()
        if (!settings.cloudCollaborativeEnabled) return CloudAiRequestOutcome.Disabled
        if (!remoteDataSource.isAvailable()) return CloudAiRequestOutcome.FirebaseUnavailable
        return runCatching {
            val deviceId = remoteDataSource.anonymousDeviceId()
            val request = event.toCloudAiAnalysisRequest(
                appVersion = appVersion,
                deviceId = deviceId,
                now = now,
                favoriteSports = settings.favoriteSports,
                favoriteCompetitions = settings.favoriteCompetitions,
                forceOpenedPriority = true,
            ) ?: return@runCatching CloudAiRequestOutcome.NotQueued("evenement non coherent pour une demande IA")
            val written = remoteDataSource.publishAiAnalysisRequests(listOf(request))
            if (written > 0) CloudAiRequestOutcome.Requested else CloudAiRequestOutcome.NotQueued("demande IA deja presente ou refusee")
        }.getOrElse { error ->
            CloudAiRequestOutcome.Failed(cloudCollaborativeErrorMessage(error))
        }
    }

    suspend fun requestAiAnalysisForPrediction(
        appVersion: String,
        prediction: PredictionEntity,
    ): CloudAiRequestOutcome {
        val now = System.currentTimeMillis()
        if (prediction.aiAnalysis.hasValidCloudAiAnalysis() && now - prediction.aiGeneratedAt <= 12 * 60 * 60 * 1000L) {
            return CloudAiRequestOutcome.AlreadyAvailable
        }
        val settings = preferencesRepository.settings.first()
        if (!settings.cloudCollaborativeEnabled) return CloudAiRequestOutcome.Disabled
        if (!remoteDataSource.isAvailable()) return CloudAiRequestOutcome.FirebaseUnavailable
        return runCatching {
            val deviceId = remoteDataSource.anonymousDeviceId()
            val request = prediction.toCloudAiAnalysisRequest(
                appVersion = appVersion,
                deviceId = deviceId,
                now = now,
                favoriteSports = settings.favoriteSports,
                favoriteCompetitions = settings.favoriteCompetitions,
                forceOpenedPriority = true,
            ) ?: return@runCatching CloudAiRequestOutcome.NotQueued("prediction non coherente ou IA deja fraiche")
            val written = remoteDataSource.publishAiAnalysisRequests(listOf(request))
            if (written > 0) CloudAiRequestOutcome.Requested else CloudAiRequestOutcome.NotQueued("demande IA deja presente ou refusee")
        }.getOrElse { error ->
            CloudAiRequestOutcome.Failed(cloudCollaborativeErrorMessage(error))
        }
    }

    private suspend fun hydratePredictionWithCloudAi(prediction: PredictionEntity): PredictionEntity {
        val now = System.currentTimeMillis()
        val settings = preferencesRepository.settings.first()
        if (!settings.cloudCollaborativeEnabled) return prediction
        if (!remoteDataSource.isAvailable()) return prediction
        return runCatching {
            // Garantit que la lecture Firestore respecte les règles auth anonyme,
            // même si l'app n'a pas encore publié de données dans cette session.
            remoteDataSource.anonymousDeviceId()
            val directCloudResults = remoteDataSource.fetchByEventIds(setOf(prediction.eventId), now)
            val cloudResults = (directCloudResults + remoteDataSource.fetchRecent(now, CLOUD_MAX_FETCH_PER_SYNC))
                .distinctBy { cloudDocumentIdFor(it.eventId) }
                .filter { RemovedSports.isAllowedSportKey(it.sport) }
            val merged = mergeCloudResults(listOf(prediction), cloudResults, now)
                .predictionsToUpsert
                .firstOrNull()
            if (merged != null) {
                predictionDao.upsertAll(listOf(merged))
                val report = CloudSyncReport(
                    enabled = true,
                    firebaseAvailable = true,
                    fetchedCount = cloudResults.size,
                    mergedCount = 1,
                    lastSyncEpoch = now,
                    lastReadEpoch = now,
                )
                preferencesRepository.updateCloudMetadata(report)
                merged
            } else {
                prediction
            }
        }.getOrElse {
            prediction
        }
    }

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

            runCatching { remoteDataSource.deleteSports(RemovedSports.keys) }
            val jobDiagnostic = runCatching { remoteDataSource.fetchDiagnostic() }
                .getOrNull()
                ?: CloudJobDiagnostic()
            val deviceId = if (publishLocal || fetchCloud) remoteDataSource.anonymousDeviceId() else ""
            val localPredictions = predictionDao.getUpcoming(now)
                .filter { RemovedSports.isAllowedSportKey(it.sportKey) }
            val localEvents = upcomingEventDao.getActive(now, now - 48 * 60 * 60 * 1000L)
                .filter { RemovedSports.isAllowedSportKey(it.sportKey) }
            var attemptedUpload = 0
            var uploaded = 0
            var lastUpload = settings.lastCloudUploadEpoch
            var uploadErrorMessage = ""
            val canUpload = forceUpload || now - settings.lastCloudUploadEpoch >= CLOUD_WRITE_INTERVAL_MS
            if (publishLocal && canUpload) {
                runCatching {
                    val predictionPayload = localPredictions
                        .asSequence()
                        .mapNotNull { it.toCloudSharedResult(appVersion, deviceId, now) }
                        .toList()
                    val predictionDocumentIds = predictionPayload
                        .asSequence()
                        .map { cloudDocumentIdFor(it.eventId) }
                        .toSet()
                    val existingDocumentIds = runCatching {
                        remoteDataSource.fetchKnownDocumentIds(now, CLOUD_MAX_KNOWN_DOCS_PER_SYNC)
                    }.getOrDefault(emptySet())
                    val calendarPayload = localEvents
                        .asSequence()
                        .mapNotNull { it.toCloudSharedCalendarEvent(appVersion, deviceId, now) }
                        .filter { event ->
                            val documentId = cloudDocumentIdFor(event.eventId)
                            documentId !in predictionDocumentIds && documentId !in existingDocumentIds
                        }
                        .toList()
                    attemptedUpload = predictionPayload.size + calendarPayload.size
                    if (predictionPayload.isNotEmpty()) {
                        uploaded += remoteDataSource.publish(predictionPayload)
                    }
                    if (calendarPayload.isNotEmpty()) {
                        uploaded += runCatching {
                            remoteDataSource.publishCalendarEvents(calendarPayload)
                        }.getOrDefault(0)
                    }
                    val aiRequestPayload = (
                        localPredictions.asSequence()
                            .mapNotNull {
                                it.toCloudAiAnalysisRequest(
                                    appVersion = appVersion,
                                    deviceId = deviceId,
                                    now = now,
                                    favoriteSports = settings.favoriteSports,
                                    favoriteCompetitions = settings.favoriteCompetitions,
                                )
                            } +
                            localEvents.asSequence()
                                .mapNotNull {
                                    it.toCloudAiAnalysisRequest(
                                        appVersion = appVersion,
                                        deviceId = deviceId,
                                        now = now,
                                        favoriteSports = settings.favoriteSports,
                                        favoriteCompetitions = settings.favoriteCompetitions,
                                    )
                                }
                        )
                        .distinctBy { cloudAiRequestDocumentIdFor(deviceId, it.eventId) }
                        .sortedByDescending { it.priority }
                        .take(36)
                        .toList()
                    attemptedUpload += aiRequestPayload.size
                    if (aiRequestPayload.isNotEmpty()) {
                        uploaded += runCatching {
                            remoteDataSource.publishAiAnalysisRequests(aiRequestPayload)
                        }.getOrDefault(0)
                    }
                    if (uploaded > 0) {
                        lastUpload = now
                    }
                }.onFailure { error ->
                    uploadErrorMessage = cloudCollaborativeErrorMessage(error)
                }
            }

            var fetchedCount = 0
            var mergedCount = 0
            var rejectedCount = 0
            var lastRead = settings.lastCloudReadEpoch
            var fetchErrorMessage = ""
            if (fetchCloud) {
                runCatching {
                    val cloudResults = remoteDataSource.fetchRecent(now, CLOUD_MAX_FETCH_PER_SYNC)
                        .filter { RemovedSports.isAllowedSportKey(it.sport) }
                    fetchedCount = cloudResults.size
                    val merge = mergeCloudResults(localPredictions, cloudResults, now)
                    rejectedCount = merge.rejectedCloudCount
                    mergedCount = merge.predictionsToUpsert.size
                    if (merge.predictionsToUpsert.isNotEmpty()) {
                        predictionDao.upsertAll(merge.predictionsToUpsert)
                    }
                    if (merge.eventsToUpsert.isNotEmpty()) {
                        upcomingEventDao.upsertAll(merge.eventsToUpsert)
                    }
                    lastRead = now
                }.onFailure { error ->
                    fetchErrorMessage = cloudCollaborativeErrorMessage(error)
                }
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
                errorMessage = when {
                    fetchErrorMessage.isNotBlank() -> fetchErrorMessage
                    fetchCloud && lastRead == now -> ""
                    else -> uploadErrorMessage
                },
                jobDiagnostic = jobDiagnostic,
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
        val jobDiagnostic = if (firebaseAvailable) {
            runCatching { remoteDataSource.fetchDiagnostic() }.getOrNull() ?: CloudJobDiagnostic()
        } else {
            CloudJobDiagnostic()
        }
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
            appendLine("githubActionStatus=${jobDiagnostic.status}")
            appendLine("githubActionStartedAt=${jobDiagnostic.startedAt}")
            appendLine("githubActionFinishedAt=${jobDiagnostic.finishedAt}")
            appendLine("githubActionEventsFound=${jobDiagnostic.eventsFound}")
            appendLine("githubActionResultsPrepared=${jobDiagnostic.resultsPrepared}")
            appendLine("githubActionResultsWritten=${jobDiagnostic.resultsWritten}")
            appendLine("githubActionRemovedSportsDeleted=${jobDiagnostic.removedSportDocumentsDeleted}")
            appendLine("githubActionSourcesChecked=${jobDiagnostic.sourcesChecked}")
            appendLine("githubActionSourceErrors=${jobDiagnostic.sourceErrorsCount}")
            appendLine("githubActionFirestoreError=${jobDiagnostic.firestoreError}")
            appendLine("githubActionFirestoreCleanupError=${jobDiagnostic.firestoreCleanupError}")
            appendLine("githubActionError=${jobDiagnostic.error}")
            appendLine("privacy=aucun historique privé, log sensible ou donnée personnelle n'est envoyé")
            appendLine("favoritesPriority=les favoris créent seulement des demandes IA prioritaires sur des événements publics")
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
            jobDiagnostic = jobDiagnostic,
        )
        preferencesRepository.updateCloudMetadata(report)
        return report
    }
}

data class CloudPreparedPrediction(
    val prediction: PredictionEntity,
    val request: CloudAiRequestOutcome,
)

sealed class CloudAiRequestOutcome(
    val status: String,
    val message: String,
) {
    data object Requested : CloudAiRequestOutcome("requested", "demande IA cloud envoyee")
    data object AlreadyAvailable : CloudAiRequestOutcome("available", "analyse IA cloud deja disponible")
    data object Disabled : CloudAiRequestOutcome("disabled", "cloud collaboratif desactive")
    data object FirebaseUnavailable : CloudAiRequestOutcome("firebase_unavailable", "Firebase indisponible ou non configure")
    data class NotQueued(private val reason: String) : CloudAiRequestOutcome("not_queued", reason)
    data class Failed(private val reason: String) : CloudAiRequestOutcome("failed", reason)
}

private fun PredictionEntity.withCloudAiRequestDiagnostic(outcome: CloudAiRequestOutcome): PredictionEntity {
    if (aiAnalysis.hasValidCloudAiAnalysis()) return this
    return copy(
        aiDiagnostic = buildString {
            append("{")
            append("\"aiRequestStatus\":\"").append(outcome.status.escapeJson()).append("\",")
            append("\"aiRequestMessage\":\"").append(outcome.message.escapeJson()).append("\",")
            append("\"aiRequestUpdatedAt\":").append(System.currentTimeMillis())
            append("}")
        },
    )
}

private fun String.escapeJson(): String =
    buildString {
        for (char in this@escapeJson) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

interface CloudCollaborativeRemoteDataSource {
    fun isAvailable(): Boolean
    fun anonymousDeviceId(): String
    fun publish(results: List<CloudSharedResult>): Int
    fun publishCalendarEvents(events: List<CloudSharedCalendarEvent>): Int
    fun publishAiAnalysisRequests(requests: List<CloudAiAnalysisRequest>): Int
    fun fetchByEventIds(eventIds: Set<String>, now: Long): List<CloudSharedResult>
    fun fetchRecent(now: Long, limit: Long): List<CloudSharedResult>
    fun fetchDiagnostic(): CloudJobDiagnostic?
    fun fetchKnownDocumentIds(now: Long, limit: Long): Set<String>
    fun deleteSports(sports: Set<String>): Int
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

    override fun publishAiAnalysisRequests(requests: List<CloudAiAnalysisRequest>): Int {
        if (requests.isEmpty()) return 0
        val firestore = firestoreOrNull() ?: return 0
        val now = System.currentTimeMillis()
        val publishable = requests
            .filter { it.isCoherent(now) }
            .distinctBy { cloudAiRequestDocumentIdFor(it.deviceId, it.eventId) }
        if (publishable.isEmpty()) return 0
        publishable.chunked(CLOUD_FIRESTORE_BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { request ->
                batch.set(
                    firestore.collection("ai_requests")
                        .document(cloudAiRequestDocumentIdFor(request.deviceId, request.eventId)),
                    request.toFirestoreMap(),
                    SetOptions.merge(),
                )
            }
            Tasks.await(batch.commit(), 15, TimeUnit.SECONDS)
        }
        return publishable.size
    }

    override fun fetchByEventIds(eventIds: Set<String>, now: Long): List<CloudSharedResult> {
        val firestore = firestoreOrNull() ?: return emptyList()
        val documentIds = eventIds
            .map { cloudDocumentIdFor(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
        if (documentIds.isEmpty()) return emptyList()
        return documentIds.flatMap { documentId ->
            listOf("cloud_results", "shared_results").mapNotNull { collectionName ->
                runCatching {
                    Tasks.await(
                        firestore.collection(collectionName).document(documentId).get(),
                        8,
                        TimeUnit.SECONDS,
                    ).data?.let(::cloudSharedResultFromMap)
                }.getOrNull()
            }
        }.filter { it.isCoherent(now) }
            .distinctBy { cloudDocumentIdFor(it.eventId) }
    }

    override fun fetchRecent(now: Long, limit: Long): List<CloudSharedResult> {
        val firestore = firestoreOrNull() ?: return emptyList()
        val perCollectionLimit = limit.coerceAtLeast(120L)
        val cloudResults = runCatching {
            fetchRecentFromCollection(firestore, "cloud_results", now, perCollectionLimit)
        }
        val sharedResults = runCatching {
            fetchRecentFromCollection(firestore, "shared_results", now, perCollectionLimit)
        }
        if (cloudResults.isFailure && sharedResults.isFailure) {
            throw cloudResults.exceptionOrNull()
                ?: sharedResults.exceptionOrNull()
                ?: IllegalStateException("Lecture Firestore indisponible")
        }
        return (cloudResults.getOrDefault(emptyList()) + sharedResults.getOrDefault(emptyList()))
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

    override fun fetchDiagnostic(): CloudJobDiagnostic? {
        val firestore = firestoreOrNull() ?: return null
        return runCatching {
            val document = Tasks.await(
                firestore.collection("cloud_diagnostics").document("current").get(),
                8,
                TimeUnit.SECONDS,
            )
            document.data?.let(::cloudJobDiagnosticFromMap)
        }.getOrNull()
    }

    override fun fetchKnownDocumentIds(now: Long, limit: Long): Set<String> {
        val firestore = firestoreOrNull() ?: return emptySet()
        return runCatching {
            val snapshot = Tasks.await(
                firestore.collection("shared_results")
                    .whereGreaterThan("expiresAt", now)
                    .limit(limit)
                    .get(),
                12,
                TimeUnit.SECONDS,
            )
            snapshot.documents.map { it.id }.toSet()
        }.getOrDefault(emptySet())
    }

    override fun deleteSports(sports: Set<String>): Int {
        if (sports.isEmpty()) return 0
        val firestore = firestoreOrNull() ?: return 0
        val normalizedSports = sports.map { it.substringBefore('/').trim() }.filter { it.isNotBlank() }.distinct()
        if (normalizedSports.isEmpty()) return 0
        var deleted = 0
        listOf("shared_results", "cloud_results").forEach { collectionName ->
            normalizedSports.chunked(10).forEach { chunk ->
                while (true) {
                    val snapshot = runCatching {
                        Tasks.await(
                            firestore.collection(collectionName)
                                .whereIn("sport", chunk)
                                .limit(CLOUD_FIRESTORE_BATCH_LIMIT.toLong())
                                .get(),
                            12,
                            TimeUnit.SECONDS,
                        )
                    }.getOrNull() ?: break
                    if (snapshot.isEmpty) break
                    val batch = firestore.batch()
                    snapshot.documents.forEach { document -> batch.delete(document.reference) }
                    Tasks.await(batch.commit(), 15, TimeUnit.SECONDS)
                    deleted += snapshot.size()
                    if (snapshot.size() < CLOUD_FIRESTORE_BATCH_LIMIT) break
                }
            }
        }
        return deleted
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

        normalized.contains("resource_exhausted") ||
            normalized.contains("quota") ||
            normalized.contains("http 429") ->
            "Quota Firestore gratuit atteint. Cache conserve, prochaine lecture quand Firebase redevient disponible."

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
