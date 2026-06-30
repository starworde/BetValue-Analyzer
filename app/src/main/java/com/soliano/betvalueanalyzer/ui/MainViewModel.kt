package com.soliano.betvalueanalyzer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.soliano.betvalueanalyzer.BuildConfig
import com.soliano.betvalueanalyzer.BetValueApplication
import com.soliano.betvalueanalyzer.data.AnalysisRepository
import com.soliano.betvalueanalyzer.data.OddsRepository
import com.soliano.betvalueanalyzer.data.PreferencesRepository
import com.soliano.betvalueanalyzer.data.SyncPriorities
import com.soliano.betvalueanalyzer.data.DeepAnalysisTarget
import com.soliano.betvalueanalyzer.data.UserSettings
import com.soliano.betvalueanalyzer.data.cloud.CloudCollaborativeRepository
import com.soliano.betvalueanalyzer.data.cloud.CloudSyncReport
import com.soliano.betvalueanalyzer.data.local.LiveEventEntity
import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.data.local.SportEntity
import com.soliano.betvalueanalyzer.data.local.UpcomingEventEntity
import com.soliano.betvalueanalyzer.domain.competitionFavoriteKey
import com.soliano.betvalueanalyzer.ui.components.StructuredAnalysisCache
import com.soliano.betvalueanalyzer.ui.components.cleanDisplayText
import com.soliano.betvalueanalyzer.ui.components.predictionCategoryKey
import com.soliano.betvalueanalyzer.ui.components.predictionCategoryLabel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Ready(val message: String) : SyncStatus
    data class Error(val message: String) : SyncStatus
}

sealed interface DeepAnalysisStatus {
    data object Idle : DeepAnalysisStatus
    data class Running(val target: DeepAnalysisTarget, val progress: Double, val stage: String) : DeepAnalysisStatus
    data class Ready(val prediction: PredictionEntity) : DeepAnalysisStatus
    data class Error(val target: DeepAnalysisTarget, val message: String) : DeepAnalysisStatus
}

data class AppUiState(
    val sports: List<SportEntity> = emptyList(),
    val predictions: List<PredictionEntity> = emptyList(),
    val upcomingEvents: List<UpcomingEventEntity> = emptyList(),
    val liveEvents: List<LiveEventEntity> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val isReady: Boolean = false,
) {
    val automaticValueBets: List<PredictionEntity>
        get() = predictions.filter { predictionCategoryKey(it.category) in setOf("safe", "mitige") }
            .sortedByDescending { it.confidenceScore }

    val topPredictions: List<PredictionEntity>
        get() = predictions.sortedWith(
            compareByDescending<PredictionEntity> { predictionIsFavorite(it) }
                .thenByDescending { predictionCategoryKey(it.category) == "safe" }
                .thenByDescending { predictionCategoryKey(it.category) == "mitige" }
                .thenByDescending { it.confidenceScore }
                .thenBy { it.commenceTime }
        )

    val favoriteUpcomingEvents: List<UpcomingEventEntity>
        get() = upcomingEvents.filter { event ->
            event.sportKey in settings.favoriteSports || event.competitionKey in settings.favoriteCompetitions
        }.sortedBy { it.commenceTime }

    val favoriteCompetitionUpcomingEvents: List<UpcomingEventEntity>
        get() = upcomingEvents.filter { event ->
            event.competitionKey in settings.favoriteCompetitions
        }.sortedWith(
            compareBy<UpcomingEventEntity> { it.commenceTime }
                .thenBy { it.sportTitle }
                .thenBy { it.competitionName }
        )

    val favoriteSportUpcomingEvents: List<UpcomingEventEntity>
        get() = upcomingEvents.filter { event ->
            event.sportKey in settings.favoriteSports && event.competitionKey !in settings.favoriteCompetitions
        }.sortedWith(
            compareBy<UpcomingEventEntity> { it.commenceTime }
                .thenBy { it.sportTitle }
                .thenBy { it.competitionName }
        )

    val hasConfiguredFavorites: Boolean
        get() = settings.favoriteSports.isNotEmpty() || settings.favoriteCompetitions.isNotEmpty()

    private fun predictionIsFavorite(prediction: PredictionEntity): Boolean {
        val sport = prediction.sportKey.substringBefore('/')
        return sport in settings.favoriteSports ||
            competitionFavoriteKey(sport, prediction.competitionName) in settings.favoriteCompetitions
    }
}

class MainViewModel(
    private val analysisRepository: AnalysisRepository,
    private val preferencesRepository: PreferencesRepository,
    private val oddsRepository: OddsRepository,
    private val cloudCollaborativeRepository: CloudCollaborativeRepository,
) : ViewModel() {
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    private val _deepAnalysis = MutableStateFlow<DeepAnalysisStatus>(DeepAnalysisStatus.Idle)
    val deepAnalysis: StateFlow<DeepAnalysisStatus> = _deepAnalysis
    private var deepAnalysisJob: Job? = null
    private var livePreloadJob: Job? = null
    private var lastLivePreloadEpoch: Long = 0L
    private val deepPredictionCache = ConcurrentHashMap<String, PredictionEntity>()
    private val deepAnalysisTasks = ConcurrentHashMap<String, Deferred<PredictionEntity>>()
    private val sportPreloadJobs = ConcurrentHashMap<String, Job>()

    private val oddsUiState = combine(
        oddsRepository.upcomingPredictions,
        oddsRepository.upcomingEvents,
        oddsRepository.liveEvents,
    ) { predictions, events, liveEvents ->
        OddsUiStreams(predictions, events, liveEvents)
    }

    private val displayOddsState = oddsUiState
        .conflate()
        .map { odds ->
            withContext(Dispatchers.Default) {
                val displayPredictions = odds.predictions
                    .map { it.cleanedForDisplay() }
                    .deduplicatedPredictionsForDisplay()
                val displayEvents = odds.events
                    .map { it.cleanedForDisplay() }
                    .deduplicatedEventsForDisplay()
                OddsUiStreams(
                    predictions = displayPredictions,
                    events = displayEvents,
                    liveEvents = odds.liveEvents.map { it.cleanedForDisplay() },
                )
            }
        }

    private val contentState = combine(
        analysisRepository.sports,
        displayOddsState,
        preferencesRepository.settings,
    ) { sports, odds, settings ->
        AppUiState(
            sports = sports,
            predictions = odds.predictions,
            upcomingEvents = odds.events,
            liveEvents = odds.liveEvents,
            settings = settings,
            isReady = true,
        )
    }

    val uiState: StateFlow<AppUiState> = combine(contentState, syncStatus) { state, status ->
        state.copy(syncStatus = status)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState(),
    )

    init {
        viewModelScope.launch {
            runCatching { analysisRepository.seedIfNeeded() }
                .onFailure { messages.emit("Initialisation impossible : ${it.message}") }
            val settings = preferencesRepository.settings.first()
            if (settings.cloudCollaborativeEnabled) {
                syncCloudInBackground(showMessage = false, publishLocal = false, fetchCloud = true)
            }
            if (settings.autoRefresh) refreshOdds(showMessage = false)
        }
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val liveRunning = uiState.value.liveEvents.any { it.isLive }
                val nextMatch = uiState.value.upcomingEvents
                    .filter { it.eventType == "MATCH" && it.commenceTime > now }
                    .minOfOrNull { it.commenceTime }
                val delayMs = if (liveRunning) {
                    60 * 1000L
                } else if (nextMatch != null && nextMatch - now <= 2 * 60 * 60 * 1000L) {
                    2 * 60 * 1000L
                } else {
                    5 * 60 * 1000L
                }
                delay(delayMs)
                if (preferencesRepository.settings.first().autoRefresh) refreshOdds(showMessage = false)
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            oddsRepository.upcomingPredictions.collectLatest { predictions ->
                val cleaned = predictions.map { it.cleanedForDisplay() }
                StructuredAnalysisCache.preload(
                    cleaned.sortedWith(
                        compareByDescending<PredictionEntity> { predictionCategoryKey(it.category) == "safe" }
                            .thenByDescending { predictionCategoryKey(it.category) == "mitige" }
                            .thenByDescending { it.confidenceScore }
                            .thenBy { it.commenceTime }
                    ),
                    limit = 48,
                )
            }
        }
    }

    fun confirmAge() = viewModelScope.launch { preferencesRepository.confirmAge() }

    fun refreshOdds(showMessage: Boolean = true, force: Boolean = false) {
        if (syncStatus.value == SyncStatus.Syncing) return
        syncStatus.value = SyncStatus.Syncing
        viewModelScope.launch {
            val settings = preferencesRepository.settings.first()
            val syncAge = System.currentTimeMillis() - settings.lastSyncEpoch
            if (!force && uiState.value.upcomingEvents.isNotEmpty() && settings.lastSyncEpoch > 0L && syncAge in 0 until 60_000L) {
                val message = "Données déjà à jour. Patientez une minute avant une nouvelle requête."
                syncStatus.value = SyncStatus.Ready(message)
                if (showMessage) messages.emit(message)
                return@launch
            }
            val priorities = SyncPriorities(settings.favoriteSports, settings.favoriteCompetitions)
            runCatching { withContext(Dispatchers.IO) { oddsRepository.syncUpcoming(priorities) } }
                .onSuccess { result ->
                    val liveResult = withContext(Dispatchers.IO) {
                        runCatching { oddsRepository.syncLive(priorities) }.getOrNull()
                    }
                    val message = if (result.eventsCataloged == 0) {
                        "Aucun événement sportif trouvé pour le moment."
                    } else {
                        buildString {
                            append("${result.eventsCataloged} événements détectés, ${result.eventsAnalyzed} analysés")
                            liveResult?.let { append(" · ${it.liveCount} live, ${it.eventsTracked} suivis aujourd'hui") }
                            append(".")
                        }
                    }
                    syncStatus.value = SyncStatus.Ready(message)
                    if (showMessage) messages.emit(message)
                    syncCloudInBackground(showMessage = false, publishLocal = true, fetchCloud = true)
                }
                .onFailure { error ->
                    val liveResult = withContext(Dispatchers.IO) {
                        runCatching { oddsRepository.syncLive(priorities) }.getOrNull()
                    }
                    val message = liveResult?.let { "Calendrier instable, mais live actualisé : ${it.liveCount} live, ${it.eventsTracked} suivis." }
                        ?: error.userSyncMessage("Synchronisation impossible.")
                    syncStatus.value = SyncStatus.Error(message)
                    if (showMessage) messages.emit(message)
                    syncCloudInBackground(showMessage = false, publishLocal = false, fetchCloud = true)
                }
        }
    }

    fun setAutoRefresh(value: Boolean) = viewModelScope.launch {
        preferencesRepository.setAutoRefresh(value)
        if (value) refreshOdds(false)
    }

    fun setCloudCollaborativeEnabled(value: Boolean) = viewModelScope.launch {
        preferencesRepository.setCloudCollaborativeEnabled(value)
        if (value) {
            syncCloudInBackground(showMessage = true, forceUpload = true, publishLocal = true, fetchCloud = true)
        } else {
            messages.emit("Cloud collaboratif désactivé.")
        }
    }

    fun forceCloudSync() {
        syncCloudInBackground(showMessage = true, forceUpload = true, publishLocal = true, fetchCloud = true)
    }

    fun exportCloudDiagnostic() = viewModelScope.launch {
        val report = withContext(Dispatchers.IO) {
            cloudCollaborativeRepository.exportDiagnostic(BuildConfig.VERSION_NAME)
        }
        val path = report.diagnosticPath.ifBlank { "diagnostic indisponible" }
        messages.emit("Diagnostic cloud exporté : $path")
    }

    fun setAppLanguage(value: String) = viewModelScope.launch {
        preferencesRepository.setAppLanguage(value)
    }

    fun refreshLive(showMessage: Boolean = true) {
        if (livePreloadJob?.isActive == true) return
        syncStatus.value = SyncStatus.Syncing
        livePreloadJob = viewModelScope.launch {
            val settings = preferencesRepository.settings.first()
            val priorities = SyncPriorities(settings.favoriteSports, settings.favoriteCompetitions)
            runCatching {
                withContext(Dispatchers.IO) { oddsRepository.syncLive(priorities) }
            }.onSuccess { result ->
                val message = "${result.liveCount} live, ${result.eventsTracked} événement(s) suivis maintenant."
                syncStatus.value = SyncStatus.Ready(message)
                if (showMessage) messages.emit(message)
            }.onFailure { error ->
                val message = error.userSyncMessage("Actualisation live impossible.")
                syncStatus.value = SyncStatus.Error(message)
                if (showMessage) messages.emit(message)
            }
        }
    }

    fun setFavoriteSport(key: String, favorite: Boolean) = viewModelScope.launch {
        preferencesRepository.setFavoriteSport(key, favorite)
        refreshOdds(showMessage = false, force = true)
    }

    fun setFavoriteCompetition(key: String, favorite: Boolean) = viewModelScope.launch {
        preferencesRepository.setFavoriteCompetition(key, favorite)
        refreshOdds(showMessage = false, force = true)
    }

    fun addCustomSport(name: String) = viewModelScope.launch {
        analysisRepository.addCustomSport(name)
        messages.emit("Sport ajouté")
    }

    fun setAnalysisOnly(value: Boolean) = viewModelScope.launch {
        preferencesRepository.setAnalysisOnly(value)
    }

    fun setPauseReminders(value: Boolean) = viewModelScope.launch {
        preferencesRepository.setPauseReminders(value)
    }

    fun analyzeEvent(event: UpcomingEventEntity) {
        eventDeepTarget(event)?.let { target ->
            launchDeepAnalysis(target)
            return
        }
        messages.tryEmit("Cet événement est suivi, mais les participants ou données détaillées ne sont pas encore confirmés.")
    }

    fun analyzePrediction(prediction: PredictionEntity) {
        val event = uiState.value.upcomingEvents.firstOrNull { candidate ->
            candidate.analysisId == prediction.id ||
                (candidate.commenceTime == prediction.commenceTime &&
                    setOf(candidate.participantA, candidate.participantB) == setOf(prediction.homeTeam, prediction.awayTeam))
        }
        launchDeepAnalysis(
            DeepAnalysisTarget(
                id = event?.id ?: prediction.eventId,
                sportKey = event?.sportKey ?: prediction.sportKey,
                sportTitle = prediction.sportTitle,
                competitionKey = event?.competitionKey
                    ?: competitionFavoriteKey(prediction.sportKey.substringBefore('/'), prediction.competitionName),
                competitionName = prediction.competitionName,
                commenceTime = prediction.commenceTime,
                homeTeam = prediction.homeTeam,
                awayTeam = prediction.awayTeam,
            )
        )
    }

    fun preloadSportAnalyses(sportKey: String) {
        if (syncStatus.value == SyncStatus.Syncing) return
        val sport = sportKey.substringBefore('/')
        sportPreloadJobs[sport]?.cancel()
        sportPreloadJobs[sport] = viewModelScope.launch(Dispatchers.Default) {
            val targets = sportPreloadTargets(sport)
            targets.forEach { target ->
                try {
                    yield()
                    startDeepAnalysisTask(target).await()
                    delay(450)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    // Le préchargement ne doit jamais bloquer l'utilisation de l'app.
                }
            }
        }
    }

    fun preloadLive() {
        val now = System.currentTimeMillis()
        if (uiState.value.liveEvents.isNotEmpty() && now - lastLivePreloadEpoch < 30_000L) return
        if (livePreloadJob?.isActive == true) return
        lastLivePreloadEpoch = now
        syncStatus.value = SyncStatus.Syncing
        livePreloadJob = viewModelScope.launch {
            val settings = preferencesRepository.settings.first()
            val priorities = SyncPriorities(settings.favoriteSports, settings.favoriteCompetitions)
            runCatching { withContext(Dispatchers.IO) { oddsRepository.syncLive(priorities) } }
                .onSuccess { result ->
                    syncStatus.value = SyncStatus.Ready("${result.liveCount} live, ${result.eventsTracked} match(s)/événement(s) suivis.")
                }
                .onFailure { error ->
                    syncStatus.value = SyncStatus.Error(error.userSyncMessage("Recherche live impossible."))
                }
        }
    }

    fun retryDeepAnalysis(target: DeepAnalysisTarget) = launchDeepAnalysis(target)

    fun dismissDeepAnalysis() {
        deepAnalysisJob?.cancel()
        deepAnalysisJob = null
        _deepAnalysis.value = DeepAnalysisStatus.Idle
    }

    fun consumeDeepAnalysis() {
        if (_deepAnalysis.value is DeepAnalysisStatus.Ready) _deepAnalysis.value = DeepAnalysisStatus.Idle
    }

    private fun launchDeepAnalysis(target: DeepAnalysisTarget) {
        deepAnalysisJob?.cancel()
        deepAnalysisJob = viewModelScope.launch {
            val cacheKey = target.deepCacheKey()
            deepPredictionCache[cacheKey]?.let { cached ->
                _deepAnalysis.value = DeepAnalysisStatus.Ready(cached)
                return@launch
            }
            deepAnalysisTasks[cacheKey]?.let { running ->
                _deepAnalysis.value = DeepAnalysisStatus.Running(target, 0.72, "Dossier préchargé en cours d'ouverture")
                runCatching { running.await() }
                    .onSuccess { prediction -> _deepAnalysis.value = DeepAnalysisStatus.Ready(prediction) }
                    .onFailure { error -> _deepAnalysis.value = DeepAnalysisStatus.Error(target, error.userSyncMessage("Analyse approfondie impossible.")) }
                return@launch
            }
            _deepAnalysis.value = DeepAnalysisStatus.Running(target, 0.03, "Préparation du dossier du match")
            runCatching {
                withContext(Dispatchers.IO) {
                    oddsRepository.analyzeDeep(target) { progress, stage ->
                        _deepAnalysis.value = DeepAnalysisStatus.Running(target, progress.coerceIn(0.03, 1.0), stage)
                    }
                }
            }.onSuccess { prediction ->
                val cleaned = prediction.cleanedForDisplay()
                deepPredictionCache[cacheKey] = cleaned
                withContext(Dispatchers.Default) { StructuredAnalysisCache.getOrBuild(cleaned) }
                _deepAnalysis.value = DeepAnalysisStatus.Ready(cleaned)
            }.onFailure { error ->
                _deepAnalysis.value = DeepAnalysisStatus.Error(target, error.userSyncMessage("Analyse approfondie impossible."))
            }
        }
    }

    private fun startDeepAnalysisTask(target: DeepAnalysisTarget): Deferred<PredictionEntity> {
        val cacheKey = target.deepCacheKey()
        deepPredictionCache[cacheKey]?.let { cached -> return CompletableDeferred(cached) }
        deepAnalysisTasks[cacheKey]?.let { return it }
        val deferred = viewModelScope.async(Dispatchers.IO) {
            val prediction = oddsRepository.analyzeDeep(target) { _, _ -> }.cleanedForDisplay()
            deepPredictionCache[cacheKey] = prediction
            withContext(Dispatchers.Default) { StructuredAnalysisCache.getOrBuild(prediction) }
            prediction
        }
        val existing = deepAnalysisTasks.putIfAbsent(cacheKey, deferred)
        if (existing != null) {
            deferred.cancel()
            return existing
        }
        deferred.invokeOnCompletion { deepAnalysisTasks.remove(cacheKey, deferred) }
        return deferred
    }

    private fun sportPreloadTargets(sportKey: String): List<DeepAnalysisTarget> {
        val state = uiState.value
        val predictionTargets = state.predictions.asSequence()
            .filter { it.sportKey.substringBefore('/') == sportKey }
            .sortedWith(
                compareByDescending<PredictionEntity> { predictionCategoryKey(it.category) == "safe" }
                    .thenByDescending { predictionCategoryKey(it.category) == "mitige" }
                    .thenByDescending { it.confidenceScore }
                    .thenBy { it.commenceTime }
            )
            .map(::predictionDeepTarget)
        val eventTargets = state.upcomingEvents.asSequence()
            .filter { it.sportKey.substringBefore('/') == sportKey }
            .sortedBy { it.commenceTime }
            .mapNotNull(::eventDeepTarget)
        return (predictionTargets + eventTargets)
            .distinctBy { it.deepCacheKey() }
            .take(preloadLimitForSport(sportKey))
            .toList()
    }

    private fun predictionDeepTarget(prediction: PredictionEntity): DeepAnalysisTarget {
        val event = uiState.value.upcomingEvents.firstOrNull { candidate ->
            candidate.analysisId == prediction.id ||
                (candidate.commenceTime == prediction.commenceTime &&
                    setOf(candidate.participantA, candidate.participantB) == setOf(prediction.homeTeam, prediction.awayTeam))
        }
        return DeepAnalysisTarget(
            id = event?.id ?: prediction.eventId,
            sportKey = event?.sportKey ?: prediction.sportKey,
            sportTitle = event?.sportTitle ?: prediction.sportTitle,
            competitionKey = event?.competitionKey
                ?: competitionFavoriteKey(prediction.sportKey.substringBefore('/'), prediction.competitionName),
            competitionName = event?.competitionName ?: prediction.competitionName,
            commenceTime = prediction.commenceTime,
            homeTeam = prediction.homeTeam,
            awayTeam = prediction.awayTeam,
        )
    }

    private fun eventDeepTarget(event: UpcomingEventEntity): DeepAnalysisTarget? {
        val sport = event.sportKey.substringBefore('/')
        if (event.eventType == "GP" || sport == "racing") {
            return DeepAnalysisTarget(
                id = event.id,
                sportKey = event.sportKey,
                sportTitle = event.sportTitle,
                competitionKey = event.competitionKey,
                competitionName = event.competitionName,
                commenceTime = event.commenceTime,
                homeTeam = event.eventName,
                awayTeam = "Grille F1",
            )
        }
        if (sport == "cycling") {
            return DeepAnalysisTarget(
                id = event.id,
                sportKey = event.sportKey,
                sportTitle = event.sportTitle,
                competitionKey = event.competitionKey,
                competitionName = event.competitionName,
                commenceTime = event.commenceTime,
                homeTeam = event.eventName,
                awayTeam = "Peloton",
            )
        }
        if (sport in TEAM_DEEP_SPORTS) {
            if (event.eventType != "MATCH" || event.participantA.isBlank() || event.participantB.isBlank()) return null
            return DeepAnalysisTarget(
                id = event.id,
                sportKey = event.sportKey,
                sportTitle = event.sportTitle,
                competitionKey = event.competitionKey,
                competitionName = event.competitionName,
                commenceTime = event.commenceTime,
                homeTeam = event.participantA,
                awayTeam = event.participantB,
            )
        }
        if (sport !in STANDALONE_DEEP_SPORTS) return null
        val primary = event.participantA.ifBlank { event.eventName.ifBlank { event.competitionName } }
        val secondary = event.participantB.ifBlank { event.sportTitle.ifBlank { "Contexte" } }
        return DeepAnalysisTarget(
            id = event.id,
            sportKey = event.sportKey,
            sportTitle = event.sportTitle,
            competitionKey = event.competitionKey,
            competitionName = event.competitionName,
            commenceTime = event.commenceTime,
            homeTeam = primary,
            awayTeam = secondary,
        )
    }

    private fun preloadLimitForSport(sportKey: String): Int = when (sportKey) {
        "soccer" -> 36
        "basketball", "baseball", "tennis", "rugby" -> 24
        else -> 14
    }

    private fun DeepAnalysisTarget.deepCacheKey(): String =
        listOf(id, sportKey, competitionName, commenceTime, homeTeam, awayTeam).joinToString("|")

    private fun syncCloudInBackground(
        showMessage: Boolean,
        forceUpload: Boolean = false,
        publishLocal: Boolean = true,
        fetchCloud: Boolean = true,
    ) {
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) {
                cloudCollaborativeRepository.sync(
                    appVersion = BuildConfig.VERSION_NAME,
                    forceUpload = forceUpload,
                    publishLocal = publishLocal,
                    fetchCloud = fetchCloud,
                )
            }
            if (showMessage) messages.emit(report.toUserMessage())
        }
    }

    class Factory(private val app: BetValueApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                app.analysisRepository,
                app.preferencesRepository,
                app.oddsRepository,
                app.cloudCollaborativeRepository,
            ) as T
        }
    }
}

private fun CloudSyncReport.toUserMessage(): String = when {
    !enabled -> "Cloud collaboratif désactivé."
    errorMessage.isNotBlank() -> "Cloud collaboratif indisponible : ${cleanDisplayText(errorMessage)}"
    else -> "Cloud collaboratif : $uploadedCount envoyé(s), $fetchedCount récupéré(s), $mergedCount ajouté(s)."
}

private data class OddsUiStreams(
    val predictions: List<PredictionEntity>,
    val events: List<UpcomingEventEntity>,
    val liveEvents: List<LiveEventEntity>,
)

private fun List<PredictionEntity>.deduplicatedPredictionsForDisplay(): List<PredictionEntity> {
    if (size < 2) return this
    val bestByEvent = LinkedHashMap<String, PredictionEntity>()
    for (prediction in this) {
        val key = prediction.displayEventKey()
        val existing = bestByEvent[key]
        if (existing == null || prediction.isBetterDisplayCandidateThan(existing)) {
            bestByEvent[key] = prediction
        }
    }
    return bestByEvent.values.toList()
}

private fun List<UpcomingEventEntity>.deduplicatedEventsForDisplay(): List<UpcomingEventEntity> {
    if (size < 2) return this
    val bestByEvent = LinkedHashMap<String, UpcomingEventEntity>()
    for (event in this) {
        val key = event.displayEventKey()
        val existing = bestByEvent[key]
        if (existing == null || event.isBetterDisplayCandidateThan(existing)) {
            bestByEvent[key] = event
        }
    }
    return bestByEvent.values.toList()
}

private fun PredictionEntity.displayEventKey(): String {
    val sport = sportKey.substringBefore('/').displayKeyPart()
    val competition = competitionName.displayKeyPart()
    val participants = normalizedPairParticipants(homeTeam, awayTeam)
        .ifBlank { normalizedParticipants(selection, market) }
        .ifBlank { eventId.displayKeyPart() }
    return listOf("prediction", sport, competition, commenceTime.displayDayBucket(), participants).joinToString("|")
}

private fun UpcomingEventEntity.displayEventKey(): String {
    val sport = sportKey.substringBefore('/').displayKeyPart()
    val competition = competitionName.displayKeyPart()
    val participants = normalizedPairParticipants(participantA, participantB)
        .ifBlank { eventName.displayKeyPart() }
        .ifBlank { id.displayKeyPart() }
    return listOf("event", sport, competition, commenceTime.displayDayBucket(), participants).joinToString("|")
}

private fun PredictionEntity.isBetterDisplayCandidateThan(other: PredictionEntity): Boolean {
    val comparator = compareBy<PredictionEntity> { predictionDisplayCategoryRank(it.category) }
        .thenBy { it.confidenceScore }
        .thenBy { it.sourceAgreement }
        .thenBy { it.sourceLastUpdate }
        .thenBy { it.sourceName.length }
    return comparator.compare(this, other) > 0
}

private fun UpcomingEventEntity.isBetterDisplayCandidateThan(other: UpcomingEventEntity): Boolean {
    val comparator = compareBy<UpcomingEventEntity> { if (it.analysisId?.isNotBlank() == true) 1 else 0 }
        .thenBy { eventSourceRank(it.sourceName) }
        .thenBy { it.sourceName.length }
        .thenByDescending { it.id.length }
    return comparator.compare(this, other) > 0
}

private fun predictionDisplayCategoryRank(category: String): Int = when (predictionCategoryKey(category)) {
    "safe" -> 3
    "mitige" -> 2
    "exotique" -> 1
    else -> 0
}

private fun eventSourceRank(sourceName: String): Int {
    val normalized = sourceName.displayKeyPart()
    return when {
        "official" in normalized || "officiel" in normalized -> 4
        "espn" in normalized -> 3
        "sportsdb" in normalized || "openligadb" in normalized -> 2
        normalized.isNotBlank() -> 1
        else -> 0
    }
}

private fun normalizedParticipants(first: String, second: String): String {
    val values = listOf(first.displayKeyPart(), second.displayKeyPart()).filter { it.isNotBlank() }
    return when {
        values.size >= 2 -> values.sorted().joinToString("~")
        values.size == 1 -> values.first()
        else -> ""
    }
}

private fun normalizedPairParticipants(first: String, second: String): String {
    val values = listOf(first.displayKeyPart(), second.displayKeyPart()).filter { it.isNotBlank() }
    return if (values.size >= 2) values.sorted().joinToString("~") else ""
}

private fun Long.displayDayBucket(): Long = (this + 2 * 60 * 60 * 1000L) / (24 * 60 * 60 * 1000L)

private fun String.displayKeyPart(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase(Locale.ROOT)
    return normalized.replace("[^a-z0-9]+".toRegex(), " ").trim()
}

private val TEAM_DEEP_SPORTS = setOf(
    "soccer",
    "basketball",
    "baseball",
    "rugby",
    "hockey",
    "football",
    "handball",
    "volleyball",
    "cricket",
    "australian_football",
)

private val STANDALONE_DEEP_SPORTS = setOf(
    "tennis",
    "golf",
    "mma",
    "boxing",
    "nascar",
    "darts",
    "athletics",
)

private fun UpcomingEventEntity.cleanedForDisplay(): UpcomingEventEntity = copy(
    sportTitle = cleanDisplayText(sportTitle),
    competitionName = cleanDisplayText(competitionName),
    eventName = cleanDisplayText(eventName),
    participantA = cleanDisplayText(participantA),
    participantB = cleanDisplayText(participantB),
    sourceName = cleanDisplayText(sourceName),
)

private fun LiveEventEntity.cleanedForDisplay(): LiveEventEntity = copy(
    sportTitle = cleanDisplayText(sportTitle),
    competitionName = cleanDisplayText(competitionName),
    eventName = cleanDisplayText(eventName),
    homeName = cleanDisplayText(homeName),
    awayName = cleanDisplayText(awayName),
    statusDescription = cleanDisplayText(statusDescription),
    sourceName = cleanDisplayText(sourceName),
    sourceDetails = cleanDisplayText(sourceDetails),
    statSummary = cleanDisplayText(statSummary),
    scenarios = cleanDisplayText(scenarios),
)

private fun Throwable.userSyncMessage(fallback: String): String {
    val raw = cleanDisplayText(message.orEmpty()).trim()
    if (raw.isBlank()) return fallback
    val technicalMarkers = listOf(
        "Attempt to invoke",
        "NullPointerException",
        "null object reference",
        "getProvider()",
        "EspnOddsDto",
        "retrofit2.",
        "java.",
        "kotlin.",
        "IndexOutOfBoundsException",
    )
    if (technicalMarkers.any { raw.contains(it, ignoreCase = true) }) {
        return "Source publique instable : certaines données ont été ignorées. Relance l’actualisation."
    }
    return raw.take(180)
}

private fun PredictionEntity.cleanedForDisplay(): PredictionEntity {
    val cleaned = copy(
        sportTitle = cleanDisplayText(sportTitle),
        competitionName = cleanDisplayText(competitionName),
        homeTeam = cleanDisplayText(homeTeam),
        awayTeam = cleanDisplayText(awayTeam),
        market = cleanDisplayText(market),
        selection = cleanDisplayText(selection),
        riskLevel = cleanDisplayText(riskLevel),
        category = predictionCategoryLabel(cleanDisplayText(category)),
        sourceName = cleanDisplayText(sourceName),
        explanation = cleanDisplayText(explanation),
        positiveArguments = cleanDisplayText(positiveArguments),
        negativeArguments = cleanDisplayText(negativeArguments),
        statSummary = cleanDisplayText(statSummary),
        scenarios = cleanDisplayText(scenarios),
        homeLineupStatus = cleanDisplayText(homeLineupStatus),
        homeLineup = cleanDisplayText(homeLineup),
        awayLineupStatus = cleanDisplayText(awayLineupStatus),
        awayLineup = cleanDisplayText(awayLineup),
        playerScenarios = cleanDisplayText(playerScenarios),
        sourceDetails = cleanDisplayText(sourceDetails),
        contextInsights = cleanDisplayText(contextInsights),
    )
    return if (cleaned.sportKey.startsWith("cycling")) cleaned.normalizedCyclingCopy() else cleaned
}

private fun PredictionEntity.normalizedCyclingCopy(): PredictionEntity = copy(
    market = market
        .replace("Analyse course - pas de " + "pari automatique", "Analyse course cycliste - surveillance"),
    selection = selection
        .replace("Attendre startlist officielle et favoris confirmes", "Surveiller startlist officielle, favoris et parcours"),
    riskLevel = riskLevel.replace("Eleve", "Élevé"),
    category = category.replace("Donnees a completer", "Données à compléter"),
    sourceName = sourceName
        .replace("Analyse cyclisme -", "Analyse cyclisme ·")
        .replace("sources verifiees", "sources vérifiées"),
    explanation = cyclingLegacyText(explanation),
    positiveArguments = cyclingLegacyText(positiveArguments),
    negativeArguments = cyclingLegacyText(negativeArguments)
        .replace("pa" + "ri fragile", "signal fragile"),
    expectedScore = cyclingLegacyText(expectedScore),
    statSummary = cyclingLegacyText(statSummary),
    scenarios = cyclingLegacyText(scenarios)
        .replace("Info|", "Calendrier|")
        .replace("Pari vainqueur/podium a eviter", "Signal vainqueur/podium à éviter")
        .replace("Pari vainqueur/podium à éviter", "Signal vainqueur/podium à éviter")
        .replace("Declencheur|", "Déclencheur|"),
)

private fun cyclingLegacyText(value: String): String = value
    .replace("detectee", "détectée")
    .replace("confirmee", "confirmée")
    .replace("Actualites", "Actualités")
    .replace("equipes", "équipes")
    .replace("reseaux", "réseaux")
    .replace("recoupes", "recoupés")
    .replace("role", "rôle")
    .replace("meteo", "météo")
    .replace("scenario", "scénario")
    .replace("plutot", "plutôt")
    .replace("prevue", "prévue")
    .replace("consultees", "consultées")
    .replace("consolides", "consolidés")
    .replace("jusqu'a", "jusqu'à")
    .replace("a eviter", "à éviter")
    .replace("favoris recoupes", "favoris recoupés")
    .replace("roles d'equipe", "rôles d'équipe")
    .replace("tombent", "tombent")
