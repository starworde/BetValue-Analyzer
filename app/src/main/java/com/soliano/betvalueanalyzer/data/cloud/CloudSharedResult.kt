package com.soliano.betvalueanalyzer.data.cloud

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.data.local.UpcomingEventEntity
import com.soliano.betvalueanalyzer.domain.RemovedSports
import com.soliano.betvalueanalyzer.domain.competitionFavoriteKey
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

private const val CLOUD_SOURCE_PREFIX = "Cloud collaboratif"
private const val MAX_CLOUD_RESULT_AGE_MS = 24 * 60 * 60 * 1000L
private const val MAX_EVENT_LOOKBACK_MS = 48 * 60 * 60 * 1000L
private const val MAX_CLOCK_SKEW_MS = 10 * 60 * 1000L
private const val MAX_TEXT_FIELD_CHARS = 1_200
private const val MAX_SCENARIO_CHARS = 2_400
private const val MAX_AI_ANALYSIS_CHARS = 6_000
private const val MAX_AI_DIAGNOSTIC_CHARS = 2_600
private const val MAX_DOCUMENT_CHARS = 18_000

data class CloudSharedResult(
    val eventId: String,
    val sport: String,
    val sportTitle: String,
    val competition: String,
    val eventName: String,
    val eventDate: Long,
    val calculatedResults: String,
    val probabilities: String,
    val scenarios: String,
    val reliability: Int,
    val appVersion: String,
    val deviceId: String,
    val updatedAt: Long,
    val expiresAt: Long,
    val predictionId: String,
    val homeTeam: String,
    val awayTeam: String,
    val market: String,
    val selection: String,
    val impliedProbability: Double,
    val consensusProbability: Double,
    val valueEdge: Double,
    val expectedValue: Double,
    val confidenceScore: Int,
    val riskLevel: String,
    val category: String,
    val sourceName: String,
    val expectedScore: String,
    val statSummary: String,
    val positiveArguments: String,
    val negativeArguments: String,
    val homeLineupStatus: String,
    val homeLineup: String,
    val awayLineupStatus: String,
    val awayLineup: String,
    val playerScenarios: String,
    val sourceDetails: String,
    val contextInsights: String,
    val sourceAgreement: Int,
    val aiAnalysis: String = "",
    val aiDiagnostic: String = "",
    val aiGeneratedAt: Long = 0L,
)

data class CloudSharedCalendarEvent(
    val eventId: String,
    val sport: String,
    val sportTitle: String,
    val competitionKey: String,
    val competition: String,
    val eventName: String,
    val eventDate: Long,
    val participantA: String,
    val participantB: String,
    val eventType: String,
    val sourceName: String,
    val appVersion: String,
    val deviceId: String,
    val updatedAt: Long,
    val expiresAt: Long,
    val analysisId: String,
)

data class CloudAiAnalysisRequest(
    val eventId: String,
    val sport: String,
    val sportTitle: String,
    val competitionKey: String,
    val competition: String,
    val eventName: String,
    val eventDate: Long,
    val participantA: String,
    val participantB: String,
    val priority: Int,
    val reason: String,
    val appVersion: String,
    val deviceId: String,
    val updatedAt: Long,
    val expiresAt: Long,
    val status: String = "pending",
)

data class CloudMergeResult(
    val predictionsToUpsert: List<PredictionEntity>,
    val eventsToUpsert: List<UpcomingEventEntity>,
    val acceptedCloudCount: Int,
    val rejectedCloudCount: Int,
)

data class CloudJobDiagnostic(
    val status: String = "",
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val configuredSports: List<String> = emptyList(),
    val eventsBySport: Map<String, Int> = emptyMap(),
    val sportsWithoutEvents: List<String> = emptyList(),
    val eventsFound: Int = 0,
    val resultsPrepared: Int = 0,
    val resultsWritten: Int = 0,
    val resultsBySport: Map<String, Int> = emptyMap(),
    val removedSportDocumentsDeleted: Int = 0,
    val sourcesChecked: Int = 0,
    val sourceErrorsCount: Int = 0,
    val sourceErrors: List<String> = emptyList(),
    val firestoreError: String = "",
    val firestoreCleanupError: String = "",
    val error: String = "",
    val aiConfigured: List<String> = emptyList(),
    val aiFreeEnabled: List<String> = emptyList(),
    val aiPaidDisabled: List<String> = emptyList(),
    val aiMode: String = "",
    val aiCalled: Int = 0,
    val aiResponded: Int = 0,
    val aiErrors: List<String> = emptyList(),
    val aiCacheHits: Int = 0,
    val aiFusionCount: Int = 0,
    val aiFallbackUsed: Int = 0,
    val aiQuotaReached: Boolean = false,
)

data class CloudSyncReport(
    val enabled: Boolean,
    val firebaseAvailable: Boolean,
    val attemptedUploadCount: Int = 0,
    val uploadedCount: Int = 0,
    val fetchedCount: Int = 0,
    val mergedCount: Int = 0,
    val rejectedCount: Int = 0,
    val lastSyncEpoch: Long = 0L,
    val lastUploadEpoch: Long = 0L,
    val lastReadEpoch: Long = 0L,
    val errorMessage: String = "",
    val diagnosticPath: String = "",
    val jobDiagnostic: CloudJobDiagnostic = CloudJobDiagnostic(),
)

fun PredictionEntity.toCloudSharedResult(
    appVersion: String,
    deviceId: String,
    now: Long,
): CloudSharedResult? {
    if (eventId.isBlank() || sportKey.isBlank() || competitionName.isBlank()) return null
    if (!RemovedSports.isAllowedSportKey(sportKey)) return null
    if (homeTeam.isBlank() && awayTeam.isBlank()) return null
    if (market.isBlank() || selection.isBlank()) return null
    if (confidenceScore !in 1..100) return null
    if (commenceTime < now - MAX_EVENT_LOOKBACK_MS) return null
    if (appVersion.isBlank() || deviceId.isBlank()) return null
    val dataUpdatedAt = sourceLastUpdate.takeIf { it > 0L }?.coerceAtMost(now) ?: now
    if (dataUpdatedAt < now - MAX_CLOUD_RESULT_AGE_MS) return null

    val event = listOf(homeTeam, awayTeam)
        .filter { it.isNotBlank() }
        .joinToString(" — ")
        .ifBlank { competitionName }
    val compactScenarios = scenarios.trimCloudText(MAX_SCENARIO_CHARS)
    val compactPlayers = playerScenarios.trimCloudText(MAX_SCENARIO_CHARS)
    val compactStats = statSummary.trimCloudText(MAX_SCENARIO_CHARS)
    val calculated = listOf(
        market,
        selection,
        expectedScore,
        compactStats,
    ).filter { it.isNotBlank() }
        .joinToString(" · ")
        .trimCloudText(MAX_SCENARIO_CHARS)
    val probabilitySummary = listOf(
        "consensus=${consensusProbability.formatCloudDouble()}",
        "implied=${impliedProbability.formatCloudDouble()}",
        "edge=${valueEdge.formatCloudDouble()}",
        "ev=${expectedValue.formatCloudDouble()}",
        "confidence=$confidenceScore",
    ).joinToString("; ")

    val expiresAt = if (commenceTime > now) {
        commenceTime + MAX_EVENT_LOOKBACK_MS
    } else {
        now + 6 * 60 * 60 * 1000L
    }

    return CloudSharedResult(
        eventId = eventId.trimCloudText(220),
        sport = sportKey.substringBefore('/').trimCloudText(80),
        sportTitle = sportTitle.trimCloudText(120),
        competition = competitionName.trimCloudText(180),
        eventName = event.trimCloudText(220),
        eventDate = commenceTime,
        calculatedResults = calculated,
        probabilities = probabilitySummary,
        scenarios = compactScenarios,
        reliability = confidenceScore,
        appVersion = appVersion.trimCloudText(40),
        deviceId = deviceId.trimCloudText(80),
        updatedAt = dataUpdatedAt,
        expiresAt = expiresAt,
        predictionId = id.trimCloudText(260),
        homeTeam = homeTeam.trimCloudText(140),
        awayTeam = awayTeam.trimCloudText(140),
        market = market.trimCloudText(160),
        selection = selection.trimCloudText(180),
        impliedProbability = impliedProbability,
        consensusProbability = consensusProbability,
        valueEdge = valueEdge,
        expectedValue = expectedValue,
        confidenceScore = confidenceScore,
        riskLevel = riskLevel.trimCloudText(80),
        category = category.trimCloudText(80),
        sourceName = sourceName.trimCloudText(160),
        expectedScore = expectedScore.trimCloudText(160),
        statSummary = compactStats,
        positiveArguments = positiveArguments.trimCloudText(MAX_TEXT_FIELD_CHARS),
        negativeArguments = negativeArguments.trimCloudText(MAX_TEXT_FIELD_CHARS),
        homeLineupStatus = homeLineupStatus.trimCloudText(180),
        homeLineup = homeLineup.trimCloudText(MAX_SCENARIO_CHARS),
        awayLineupStatus = awayLineupStatus.trimCloudText(180),
        awayLineup = awayLineup.trimCloudText(MAX_SCENARIO_CHARS),
        playerScenarios = compactPlayers,
        sourceDetails = sourceDetails.trimCloudText(MAX_SCENARIO_CHARS),
        contextInsights = contextInsights.trimCloudText(MAX_SCENARIO_CHARS),
        sourceAgreement = sourceAgreement.coerceIn(0, 100),
        aiAnalysis = aiAnalysis.trimCloudText(MAX_AI_ANALYSIS_CHARS),
        aiDiagnostic = aiDiagnostic.trimCloudText(MAX_AI_DIAGNOSTIC_CHARS),
        aiGeneratedAt = aiGeneratedAt.takeIf { it > 0L } ?: 0L,
    ).takeIf { it.isCoherent(now) }
}

fun UpcomingEventEntity.toCloudSharedCalendarEvent(
    appVersion: String,
    deviceId: String,
    now: Long,
): CloudSharedCalendarEvent? {
    if (id.isBlank() || sportKey.isBlank() || competitionName.isBlank()) return null
    if (!RemovedSports.isAllowedSportKey(sportKey)) return null
    if (eventName.isBlank() && participantA.isBlank() && participantB.isBlank()) return null
    if (commenceTime < now - MAX_EVENT_LOOKBACK_MS) return null
    if (appVersion.isBlank() || deviceId.isBlank()) return null
    val expiresAt = if (commenceTime > now) {
        commenceTime + MAX_EVENT_LOOKBACK_MS
    } else {
        now + 6 * 60 * 60 * 1000L
    }
    return CloudSharedCalendarEvent(
        eventId = id.trimCloudText(220),
        sport = sportKey.substringBefore('/').trimCloudText(80),
        sportTitle = sportTitle.trimCloudText(120),
        competitionKey = competitionKey.trimCloudText(180),
        competition = competitionName.trimCloudText(180),
        eventName = eventName.trimCloudText(220),
        eventDate = commenceTime,
        participantA = participantA.trimCloudText(140),
        participantB = participantB.trimCloudText(140),
        eventType = eventType.trimCloudText(60),
        sourceName = sourceName.trimCloudText(160),
        appVersion = appVersion.trimCloudText(40),
        deviceId = deviceId.trimCloudText(80),
        updatedAt = now,
        expiresAt = expiresAt,
        analysisId = analysisId.orEmpty().trimCloudText(260),
    ).takeIf { it.isCoherent(now) }
}

fun PredictionEntity.toCloudAiAnalysisRequest(
    appVersion: String,
    deviceId: String,
    now: Long,
    favoriteSports: Set<String>,
    favoriteCompetitions: Set<String>,
): CloudAiAnalysisRequest? {
    val sport = sportKey.substringBefore('/').trim()
    if (eventId.isBlank() || sport.isBlank() || competitionName.isBlank()) return null
    if (!RemovedSports.isAllowedSportKey(sport)) return null
    if (appVersion.isBlank() || deviceId.isBlank()) return null
    if (commenceTime < now - MAX_EVENT_LOOKBACK_MS) return null
    val competitionKey = competitionFavoriteKey(sport, competitionName)
    val sportFavorite = sport in favoriteSports
    val competitionFavorite = competitionKey in favoriteCompetitions
    if (!sportFavorite && !competitionFavorite) return null
    val alreadyFresh = aiAnalysis.hasValidCloudAiAnalysis() &&
        aiGeneratedAt > 0L &&
        now - aiGeneratedAt <= 12 * 60 * 60 * 1000L
    if (alreadyFresh) return null
    val startsInMinutes = (commenceTime - now) / 60_000L
    val urgency = when {
        startsInMinutes in -120..180 -> 35
        startsInMinutes in 181..(72 * 60) -> 22
        startsInMinutes in (72 * 60 + 1)..(14 * 24 * 60) -> 10
        else -> 0
    }
    val favoriteWeight = (if (competitionFavorite) 90 else 0) + (if (sportFavorite) 55 else 0)
    val event = listOf(homeTeam, awayTeam)
        .filter { it.isNotBlank() }
        .joinToString(" â€” ")
        .ifBlank { competitionName }
    return CloudAiAnalysisRequest(
        eventId = eventId.trimCloudText(220),
        sport = sport.trimCloudText(80),
        sportTitle = sportTitle.ifBlank { sport }.trimCloudText(120),
        competitionKey = competitionKey.trimCloudText(180),
        competition = competitionName.trimCloudText(180),
        eventName = event.trimCloudText(220),
        eventDate = commenceTime,
        participantA = homeTeam.trimCloudText(140),
        participantB = awayTeam.trimCloudText(140),
        priority = (favoriteWeight + urgency + confidenceScore.coerceIn(0, 100) / 10).coerceIn(1, 200),
        reason = when {
            competitionFavorite && sportFavorite -> "competition_and_sport_priority"
            competitionFavorite -> "competition_priority"
            else -> "sport_priority"
        },
        appVersion = appVersion.trimCloudText(40),
        deviceId = deviceId.trimCloudText(80),
        updatedAt = now,
        expiresAt = if (commenceTime > now) commenceTime + MAX_EVENT_LOOKBACK_MS else now + 6 * 60 * 60 * 1000L,
    ).takeIf { it.isCoherent(now) }
}

fun UpcomingEventEntity.toCloudAiAnalysisRequest(
    appVersion: String,
    deviceId: String,
    now: Long,
    favoriteSports: Set<String>,
    favoriteCompetitions: Set<String>,
): CloudAiAnalysisRequest? {
    val sport = sportKey.substringBefore('/').trim()
    if (id.isBlank() || sport.isBlank() || competitionName.isBlank()) return null
    if (!RemovedSports.isAllowedSportKey(sport)) return null
    if (appVersion.isBlank() || deviceId.isBlank()) return null
    if (commenceTime < now - MAX_EVENT_LOOKBACK_MS) return null
    val resolvedCompetitionKey = competitionKey.ifBlank { competitionFavoriteKey(sport, competitionName) }
    val sportFavorite = sport in favoriteSports
    val competitionFavorite = resolvedCompetitionKey in favoriteCompetitions
    if (!sportFavorite && !competitionFavorite) return null
    val startsInMinutes = (commenceTime - now) / 60_000L
    val urgency = when {
        startsInMinutes in -120..180 -> 35
        startsInMinutes in 181..(72 * 60) -> 22
        startsInMinutes in (72 * 60 + 1)..(14 * 24 * 60) -> 10
        else -> 0
    }
    val compactEventName = eventName.ifBlank {
        listOf(participantA, participantB).filter { it.isNotBlank() }.joinToString(" â€” ")
    }.ifBlank { competitionName }
    return CloudAiAnalysisRequest(
        eventId = id.trimCloudText(220),
        sport = sport.trimCloudText(80),
        sportTitle = sportTitle.ifBlank { sport }.trimCloudText(120),
        competitionKey = resolvedCompetitionKey.trimCloudText(180),
        competition = competitionName.trimCloudText(180),
        eventName = compactEventName.trimCloudText(220),
        eventDate = commenceTime,
        participantA = participantA.trimCloudText(140),
        participantB = participantB.trimCloudText(140),
        priority = ((if (competitionFavorite) 90 else 0) + (if (sportFavorite) 55 else 0) + urgency).coerceIn(1, 200),
        reason = when {
            competitionFavorite && sportFavorite -> "competition_and_sport_priority"
            competitionFavorite -> "competition_priority"
            else -> "sport_priority"
        },
        appVersion = appVersion.trimCloudText(40),
        deviceId = deviceId.trimCloudText(80),
        updatedAt = now,
        expiresAt = if (commenceTime > now) commenceTime + MAX_EVENT_LOOKBACK_MS else now + 6 * 60 * 60 * 1000L,
    ).takeIf { it.isCoherent(now) }
}

fun CloudSharedResult.isCoherent(now: Long): Boolean {
    if (eventId.isBlank() || sport.isBlank() || competition.isBlank() || eventName.isBlank()) return false
    if (!RemovedSports.isAllowedSportKey(sport)) return false
    if (eventDate <= 0L || updatedAt <= 0L || expiresAt <= 0L) return false
    if (updatedAt > now + MAX_CLOCK_SKEW_MS) return false
    if (updatedAt < now - MAX_CLOUD_RESULT_AGE_MS) return false
    if (eventDate < now - MAX_EVENT_LOOKBACK_MS) return false
    if (expiresAt <= now) return false
    if (market.isBlank() || selection.isBlank()) return false
    if (reliability !in 1..100 || confidenceScore !in 1..100) return false
    if (!impliedProbability.isCloudProbability() || !consensusProbability.isCloudProbability()) return false
    if (calculatedResults.isBlank() && scenarios.isBlank() && statSummary.isBlank()) return false
    return toFirestoreMap().stringPayloadSize() <= MAX_DOCUMENT_CHARS
}

fun CloudSharedCalendarEvent.isCoherent(now: Long): Boolean {
    if (eventId.isBlank() || sport.isBlank() || competition.isBlank() || eventName.isBlank()) return false
    if (!RemovedSports.isAllowedSportKey(sport)) return false
    if (eventDate <= 0L || updatedAt <= 0L || expiresAt <= 0L) return false
    if (updatedAt > now + MAX_CLOCK_SKEW_MS) return false
    if (eventDate < now - MAX_EVENT_LOOKBACK_MS) return false
    if (expiresAt <= now) return false
    return toFirestoreMap().stringPayloadSize() <= MAX_DOCUMENT_CHARS
}

fun CloudAiAnalysisRequest.isCoherent(now: Long): Boolean {
    if (eventId.isBlank() || sport.isBlank() || competition.isBlank() || eventName.isBlank()) return false
    if (!RemovedSports.isAllowedSportKey(sport)) return false
    if (eventDate <= 0L || updatedAt <= 0L || expiresAt <= 0L) return false
    if (updatedAt > now + MAX_CLOCK_SKEW_MS) return false
    if (updatedAt < now - MAX_CLOUD_RESULT_AGE_MS) return false
    if (eventDate < now - MAX_EVENT_LOOKBACK_MS) return false
    if (expiresAt <= now) return false
    if (priority !in 1..200) return false
    if (status != "pending") return false
    return toFirestoreMap().stringPayloadSize() <= 3_800
}

fun CloudSharedResult.toPredictionEntity(local: PredictionEntity? = null): PredictionEntity {
    val source = if (sourceName.startsWith(CLOUD_SOURCE_PREFIX, ignoreCase = true)) {
        sourceName
    } else {
        "$CLOUD_SOURCE_PREFIX · ${sourceName.ifBlank { appVersion }}"
    }
    val validCloudAi = aiAnalysis.takeIf { it.hasValidCloudAiAnalysis() }.orEmpty()
    val localValidAi = local?.aiAnalysis?.takeIf { it.hasValidCloudAiAnalysis() }.orEmpty()
    val mergedAiAnalysis = validCloudAi.ifBlank { localValidAi }
    val mergedAiDiagnostic = when {
        validCloudAi.isNotBlank() -> aiDiagnostic
        localValidAi.isNotBlank() -> local?.aiDiagnostic.orEmpty()
        else -> ""
    }
    val mergedAiGeneratedAt = when {
        validCloudAi.isNotBlank() -> aiGeneratedAt
        localValidAi.isNotBlank() -> local?.aiGeneratedAt ?: 0L
        else -> 0L
    }
    return PredictionEntity(
        id = predictionId.ifBlank { local?.id ?: "cloud:${cloudDocumentIdFor(eventId)}" },
        eventId = eventId,
        sportKey = sport,
        sportTitle = sportTitle.ifBlank { sport },
        competitionName = competition,
        commenceTime = eventDate,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        market = market,
        selection = selection,
        referenceOdds = local?.referenceOdds ?: 0.0,
        impliedProbability = impliedProbability,
        consensusProbability = consensusProbability,
        valueEdge = valueEdge,
        expectedValue = expectedValue,
        confidenceScore = confidenceScore,
        riskLevel = riskLevel,
        category = category,
        bookmakerCount = local?.bookmakerCount ?: 0,
        sourceName = source,
        sourceLastUpdate = updatedAt,
        explanation = local?.explanation.orEmpty().ifBlank { calculatedResults },
        positiveArguments = positiveArguments,
        negativeArguments = negativeArguments,
        expectedScore = expectedScore,
        statSummary = statSummary,
        scenarios = scenarios,
        homeLineupStatus = homeLineupStatus,
        homeLineup = homeLineup,
        awayLineupStatus = awayLineupStatus,
        awayLineup = awayLineup,
        playerScenarios = playerScenarios,
        sourceDetails = sourceDetails,
        contextInsights = contextInsights,
        sourceAgreement = sourceAgreement,
        aiAnalysis = mergedAiAnalysis,
        aiDiagnostic = mergedAiDiagnostic,
        aiGeneratedAt = mergedAiGeneratedAt,
    )
}

fun CloudSharedResult.toUpcomingEventEntity(analysisId: String): UpcomingEventEntity = UpcomingEventEntity(
    id = "cloud:${cloudDocumentIdFor(eventId)}",
    sportKey = sport,
    sportTitle = sportTitle.ifBlank { sport },
    competitionKey = competitionFavoriteKey(sport, competition),
    competitionName = competition,
    commenceTime = eventDate,
    eventName = eventName.ifBlank {
        listOf(homeTeam, awayTeam).filter { it.isNotBlank() }.joinToString(" — ")
    }.ifBlank { competition },
    participantA = homeTeam,
    participantB = awayTeam,
    eventType = inferredCloudEventType(),
    sourceName = sourceName.ifBlank { "$CLOUD_SOURCE_PREFIX · $appVersion" },
    analysisId = analysisId.ifBlank { predictionId.ifBlank { "cloud:${cloudDocumentIdFor(eventId)}" } },
)

private fun CloudSharedResult.inferredCloudEventType(): String = when {
    homeTeam.isNotBlank() && awayTeam.isNotBlank() -> "MATCH"
    sport == "racing" -> "GP"
    sport == "cycling" || sport == "nascar" -> "RACE"
    sport == "golf" || sport == "tennis" -> "TOURNAMENT"
    else -> "EVENT"
}

fun mergeCloudResults(
    localPredictions: List<PredictionEntity>,
    cloudResults: List<CloudSharedResult>,
    now: Long,
): CloudMergeResult {
    val localsByEvent = localPredictions.associateBy { it.cloudIdentityKey() }
    var rejected = 0
    val acceptedClouds = cloudResults
        .asSequence()
        .filter { cloud ->
            val valid = cloud.isCoherent(now)
            if (!valid) rejected += 1
            valid
        }
        .sortedByDescending { it.updatedAt }
        .distinctBy { it.cloudIdentityKey() }
        .mapNotNull { cloud ->
            val local = findMatchingLocalPrediction(localPredictions, localsByEvent, cloud)
            when (cloudMergeMode(local, cloud, now)) {
                CloudMergeMode.Full -> cloud to cloud.toPredictionEntity(local)
                CloudMergeMode.AiOnly -> cloud to local!!.withCloudAiFrom(cloud)
                CloudMergeMode.None -> null
            }
        }
        .distinctBy { it.second.id }
        .toList()
    val acceptedPredictions = acceptedClouds.map { it.second }
    val acceptedEvents = acceptedClouds.map { (cloud, prediction) ->
        cloud.toUpcomingEventEntity(prediction.id)
    }
    return CloudMergeResult(
        predictionsToUpsert = acceptedPredictions,
        eventsToUpsert = acceptedEvents,
        acceptedCloudCount = acceptedPredictions.size,
        rejectedCloudCount = rejected,
    )
}

fun shouldUseCloudResult(local: PredictionEntity?, cloud: CloudSharedResult, now: Long): Boolean {
    return cloudMergeMode(local, cloud, now) != CloudMergeMode.None
}

private enum class CloudMergeMode {
    Full,
    AiOnly,
    None,
}

private fun cloudMergeMode(local: PredictionEntity?, cloud: CloudSharedResult, now: Long): CloudMergeMode {
    if (!cloud.isCoherent(now)) return CloudMergeMode.None
    if (local == null) return CloudMergeMode.Full
    if (local.selection.isBlank() || local.market.isBlank()) return CloudMergeMode.Full
    if (local.commenceTime < now - MAX_EVENT_LOOKBACK_MS) return CloudMergeMode.Full
    if (cloud.updatedAt > local.sourceLastUpdate) return CloudMergeMode.Full
    return if (cloud.shouldImportOnlyAiInto(local)) CloudMergeMode.AiOnly else CloudMergeMode.None
}

private fun CloudSharedResult.shouldImportOnlyAiInto(local: PredictionEntity): Boolean {
    if (!aiAnalysis.hasValidCloudAiAnalysis()) return false
    if (!local.aiAnalysis.hasValidCloudAiAnalysis()) return true
    return aiGeneratedAt > local.aiGeneratedAt
}

private fun PredictionEntity.withCloudAiFrom(cloud: CloudSharedResult): PredictionEntity = copy(
    aiAnalysis = cloud.aiAnalysis.takeIf { it.hasValidCloudAiAnalysis() }.orEmpty(),
    aiDiagnostic = cloud.aiDiagnostic,
    aiGeneratedAt = cloud.aiGeneratedAt,
)

private fun findMatchingLocalPrediction(
    localPredictions: List<PredictionEntity>,
    localsByEvent: Map<String, PredictionEntity>,
    cloud: CloudSharedResult,
): PredictionEntity? {
    return localsByEvent[cloud.cloudIdentityKey()]
        ?: localPredictions.firstOrNull { it.fuzzyMatches(cloud) }
}

fun CloudSharedResult.toFirestoreMap(): Map<String, Any> = linkedMapOf(
    "documentType" to "prediction",
    "eventId" to eventId,
    "sport" to sport,
    "sportTitle" to sportTitle,
    "competition" to competition,
    "eventName" to eventName,
    "eventDate" to eventDate,
    "calculatedResults" to calculatedResults,
    "probabilities" to probabilities,
    "scenarios" to scenarios,
    "reliability" to reliability,
    "appVersion" to appVersion,
    "deviceId" to deviceId,
    "updatedAt" to updatedAt,
    "expiresAt" to expiresAt,
    "predictionId" to predictionId,
    "homeTeam" to homeTeam,
    "awayTeam" to awayTeam,
    "market" to market,
    "selection" to selection,
    "impliedProbability" to impliedProbability,
    "consensusProbability" to consensusProbability,
    "valueEdge" to valueEdge,
    "expectedValue" to expectedValue,
    "confidenceScore" to confidenceScore,
    "riskLevel" to riskLevel,
    "category" to category,
    "sourceName" to sourceName,
    "expectedScore" to expectedScore,
    "statSummary" to statSummary,
    "positiveArguments" to positiveArguments,
    "negativeArguments" to negativeArguments,
    "homeLineupStatus" to homeLineupStatus,
    "homeLineup" to homeLineup,
    "awayLineupStatus" to awayLineupStatus,
    "awayLineup" to awayLineup,
    "playerScenarios" to playerScenarios,
    "sourceDetails" to sourceDetails,
    "contextInsights" to contextInsights,
    "sourceAgreement" to sourceAgreement,
    "aiAnalysis" to aiAnalysis,
    "aiDiagnostic" to aiDiagnostic,
    "aiGeneratedAt" to aiGeneratedAt,
)

fun CloudSharedCalendarEvent.toFirestoreMap(): Map<String, Any> {
    val compactEventName = eventName.ifBlank {
        listOf(participantA, participantB).filter { it.isNotBlank() }.joinToString(" — ")
    }.ifBlank { competition }
    return linkedMapOf(
        "documentType" to "calendar_event",
        "eventId" to eventId,
        "sport" to sport,
        "sportTitle" to sportTitle,
        "competitionKey" to competitionKey,
        "competition" to competition,
        "eventName" to compactEventName,
        "eventDate" to eventDate,
        "participantA" to participantA,
        "participantB" to participantB,
        "eventType" to eventType,
        "sourceName" to sourceName,
        "appVersion" to appVersion,
        "deviceId" to deviceId,
        "updatedAt" to updatedAt,
        "expiresAt" to expiresAt,
        "analysisId" to analysisId,
        // Champs de compatibilité avec les règles/lecteurs existants : ce document reste un calendrier,
        // pas un pronostic affichable.
        "calculatedResults" to "Calendrier détecté",
        "probabilities" to "",
        "scenarios" to "",
        "reliability" to 1,
        "predictionId" to "",
        "homeTeam" to participantA,
        "awayTeam" to participantB,
        "market" to "Calendrier",
        "selection" to compactEventName,
        "impliedProbability" to 0.0,
        "consensusProbability" to 0.0,
        "valueEdge" to 0.0,
        "expectedValue" to 0.0,
        "confidenceScore" to 1,
        "riskLevel" to "",
        "category" to "calendar",
        "expectedScore" to "",
        "statSummary" to "",
        "positiveArguments" to "",
        "negativeArguments" to "",
        "homeLineupStatus" to "",
        "homeLineup" to "",
        "awayLineupStatus" to "",
        "awayLineup" to "",
        "playerScenarios" to "",
        "sourceDetails" to sourceName,
        "contextInsights" to "",
        "sourceAgreement" to 0,
        "aiAnalysis" to "",
        "aiDiagnostic" to "",
        "aiGeneratedAt" to 0L,
    )
}

fun CloudAiAnalysisRequest.toFirestoreMap(): Map<String, Any> = linkedMapOf(
    "documentType" to "ai_request",
    "eventId" to eventId,
    "sport" to sport,
    "sportTitle" to sportTitle,
    "competitionKey" to competitionKey,
    "competition" to competition,
    "eventName" to eventName,
    "eventDate" to eventDate,
    "participantA" to participantA,
    "participantB" to participantB,
    "priority" to priority,
    "reason" to reason,
    "appVersion" to appVersion,
    "deviceId" to deviceId,
    "updatedAt" to updatedAt,
    "expiresAt" to expiresAt,
    "status" to status,
)

fun cloudSharedResultFromMap(map: Map<String, Any?>): CloudSharedResult? = runCatching {
    if (map.stringValue("documentType").equals("calendar_event", ignoreCase = true)) return@runCatching null
    CloudSharedResult(
        eventId = map.stringValue("eventId"),
        sport = map.stringValue("sport"),
        sportTitle = map.stringValue("sportTitle"),
        competition = map.stringValue("competition"),
        eventName = map.stringValue("eventName"),
        eventDate = map.longValue("eventDate"),
        calculatedResults = map.stringValue("calculatedResults"),
        probabilities = map.stringValue("probabilities"),
        scenarios = map.stringValue("scenarios"),
        reliability = map.intValue("reliability"),
        appVersion = map.stringValue("appVersion"),
        deviceId = map.stringValue("deviceId"),
        updatedAt = map.longValue("updatedAt"),
        expiresAt = map.longValue("expiresAt"),
        predictionId = map.stringValue("predictionId"),
        homeTeam = map.stringValue("homeTeam"),
        awayTeam = map.stringValue("awayTeam"),
        market = map.stringValue("market"),
        selection = map.stringValue("selection"),
        impliedProbability = map.doubleValue("impliedProbability"),
        consensusProbability = map.doubleValue("consensusProbability"),
        valueEdge = map.doubleValue("valueEdge"),
        expectedValue = map.doubleValue("expectedValue"),
        confidenceScore = map.intValue("confidenceScore"),
        riskLevel = map.stringValue("riskLevel"),
        category = map.stringValue("category"),
        sourceName = map.stringValue("sourceName"),
        expectedScore = map.stringValue("expectedScore"),
        statSummary = map.stringValue("statSummary"),
        positiveArguments = map.stringValue("positiveArguments"),
        negativeArguments = map.stringValue("negativeArguments"),
        homeLineupStatus = map.stringValue("homeLineupStatus"),
        homeLineup = map.stringValue("homeLineup"),
        awayLineupStatus = map.stringValue("awayLineupStatus"),
        awayLineup = map.stringValue("awayLineup"),
        playerScenarios = map.stringValue("playerScenarios"),
        sourceDetails = map.stringValue("sourceDetails"),
        contextInsights = map.stringValue("contextInsights"),
        sourceAgreement = map.intValue("sourceAgreement"),
        aiAnalysis = map.stringValue("aiAnalysis", MAX_AI_ANALYSIS_CHARS),
        aiDiagnostic = map.stringValue("aiDiagnostic", MAX_AI_DIAGNOSTIC_CHARS),
        aiGeneratedAt = map.longValue("aiGeneratedAt"),
    )
}.getOrNull()

fun cloudJobDiagnosticFromMap(map: Map<String, Any?>): CloudJobDiagnostic = CloudJobDiagnostic(
    status = map.stringValue("status"),
    startedAt = map.longValue("startedAt"),
    finishedAt = map.longValue("finishedAt"),
    updatedAt = map.longValue("updatedAt"),
    configuredSports = map.stringListValue("configuredSports"),
    eventsBySport = map.intMapValue("eventsBySport"),
    sportsWithoutEvents = map.stringListValue("sportsWithoutEvents"),
    eventsFound = map.intValue("eventsFound"),
    resultsPrepared = map.intValue("resultsPrepared"),
    resultsWritten = map.intValue("resultsWritten"),
    resultsBySport = map.intMapValue("resultsBySport"),
    removedSportDocumentsDeleted = map.intValue("removedSportDocumentsDeleted"),
    sourcesChecked = map.intValue("sourcesChecked"),
    sourceErrorsCount = (map["sourceErrors"] as? List<*>)?.size ?: map.intValue("sourceErrorsCount"),
    sourceErrors = map.sourceErrorListValue("sourceErrors"),
    firestoreError = map.stringValue("firestoreError"),
    firestoreCleanupError = map.stringValue("firestoreCleanupError"),
    error = map.stringValue("error"),
    aiConfigured = map.stringListValue("aiConfigured"),
    aiFreeEnabled = map.stringListValue("aiFreeEnabled"),
    aiPaidDisabled = map.stringListValue("aiPaidDisabled"),
    aiMode = map.stringValue("aiMode"),
    aiCalled = map.intValue("aiCalled"),
    aiResponded = map.intValue("aiResponded"),
    aiErrors = map.sourceErrorListValue("aiErrors"),
    aiCacheHits = map.intValue("aiCacheHits"),
    aiFusionCount = map.intValue("aiFusionCount"),
    aiFallbackUsed = map.intValue("aiFallbackUsed"),
    aiQuotaReached = map.booleanValue("aiQuotaReached"),
)

fun cloudDocumentIdFor(eventId: String): String =
    eventId.trim()
        .ifBlank { "unknown-event" }
        .replace(Regex("[^A-Za-z0-9_-]+"), "_")
        .trim('_')
        .take(180)
        .ifBlank { "unknown-event" }

fun cloudAiRequestDocumentIdFor(deviceId: String, eventId: String): String =
    cloudDocumentIdFor("${deviceId.takeLast(36)}_$eventId")

private fun PredictionEntity.cloudIdentityKey(): String =
    listOf(eventId, sportKey.substringBefore('/'), homeTeam, awayTeam, commenceTime.roundToMinute())
        .joinToString("|")
        .lowercase(Locale.US)

private fun CloudSharedResult.cloudIdentityKey(): String =
    listOf(eventId, sport, homeTeam, awayTeam, eventDate.roundToMinute())
        .joinToString("|")
        .lowercase(Locale.US)

private fun PredictionEntity.fuzzyMatches(cloud: CloudSharedResult): Boolean {
    if (!sportKey.substringBefore('/').cloudTextKey().equals(cloud.sport.cloudTextKey(), ignoreCase = true)) return false
    if (abs(commenceTime - cloud.eventDate) > 45 * 60 * 1000L) return false
    if (!competitionName.cloudCompatibleWith(cloud.competition)) return false
    return participantPairKey() == cloud.participantPairKey()
}

private fun PredictionEntity.participantPairKey(): String =
    listOf(homeTeam, awayTeam)
        .map { it.cloudTextKey() }
        .filter { it.isNotBlank() }
        .sorted()
        .joinToString("|")

private fun CloudSharedResult.participantPairKey(): String =
    listOf(homeTeam, awayTeam)
        .map { it.cloudTextKey() }
        .filter { it.isNotBlank() }
        .sorted()
        .joinToString("|")

private fun String.cloudCompatibleWith(other: String): Boolean {
    val left = cloudTextKey()
    val right = other.cloudTextKey()
    if (left.isBlank() || right.isBlank()) return true
    if (left == right || left.contains(right) || right.contains(left)) return true
    val leftTokens = left.split(' ').filter { it.length >= 4 }.toSet()
    val rightTokens = right.split(' ').filter { it.length >= 4 }.toSet()
    return leftTokens.intersect(rightTokens).size >= 2
}

private fun String.cloudTextKey(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

internal fun String.hasValidCloudAiAnalysis(): Boolean = runCatching {
    val parsed = JsonParser.parseString(this).takeIf { it.isJsonObject }?.asJsonObject ?: return@runCatching false
    val source = parsed.aiText("source").cloudTextKey()
    val providerCount = parsed.aiInt("providerCount")
    providerCount > 0 &&
        source.isNotBlank() &&
        "fallback" !in source &&
        "local preanalysis" !in source &&
        "local" !in source
}.getOrDefault(false)

private fun JsonObject.aiText(key: String): String =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

private fun JsonObject.aiInt(key: String): Int =
    runCatching {
        val element = get(key) ?: return 0
        if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) element.asInt
        else element.asString.filter(Char::isDigit).toIntOrNull() ?: 0
    }.getOrDefault(0)

private fun Long.roundToMinute(): Long = this / 60_000L

private fun String.trimCloudText(max: Int): String =
    replace(Regex("\\s+"), " ").trim().take(max)

private fun Double.formatCloudDouble(): String =
    if (isFinite()) String.format(Locale.US, "%.4f", this) else "0.0000"

private fun Double.isCloudProbability(): Boolean =
    isFinite() && this >= 0.0 && this <= 1.0

private fun Map<String, Any>.stringPayloadSize(): Int =
    values.sumOf { value -> if (value is String) value.length else 16 }

private fun Map<String, Any?>.stringValue(key: String, max: Int = MAX_SCENARIO_CHARS): String =
    (this[key] as? String).orEmpty().trimCloudText(max)

private fun Map<String, Any?>.longValue(key: String): Long =
    when (val value = this[key]) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

private fun Map<String, Any?>.stringListValue(key: String): List<String> =
    (this[key] as? List<*>)
        .orEmpty()
        .mapNotNull { value -> value?.toString()?.trimCloudText(160)?.takeIf { it.isNotBlank() } }
        .distinct()

private fun Map<String, Any?>.intMapValue(key: String): Map<String, Int> =
    (this[key] as? Map<*, *>)
        .orEmpty()
        .mapNotNull { (rawKey, rawValue) ->
            val name = rawKey?.toString()?.trimCloudText(80)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val count = when (rawValue) {
                is Number -> rawValue.toInt()
                is String -> rawValue.toIntOrNull() ?: 0
                else -> 0
            }
            name to count
        }
        .toMap()

private fun Map<String, Any?>.sourceErrorListValue(key: String): List<String> =
    (this[key] as? List<*>)
        .orEmpty()
        .mapNotNull { item ->
            when (item) {
                is Map<*, *> -> {
                    val source = item["source"] ?: item["name"] ?: item["sport"] ?: item["url"]
                    val error = item["error"] ?: item["message"] ?: item["status"]
                    listOfNotNull(source, error)
                        .joinToString(" : ") { it.toString().trimCloudText(120) }
                        .takeIf { it.isNotBlank() }
                }
                else -> item?.toString()?.trimCloudText(220)
            }
        }
        .filter { it.isNotBlank() }
        .distinct()

private fun Map<String, Any?>.intValue(key: String): Int =
    longValue(key).toInt()

private fun Map<String, Any?>.doubleValue(key: String): Double =
    when (val value = this[key]) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }.takeIf { it.isFinite() && abs(it) < 1_000_000.0 } ?: 0.0

private fun Map<String, Any?>.booleanValue(key: String): Boolean =
    when (val value = this[key]) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        is Number -> value.toInt() != 0
        else -> false
    }
