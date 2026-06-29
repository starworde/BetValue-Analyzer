package com.soliano.betvalueanalyzer.data.cloud

import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.data.local.UpcomingEventEntity
import java.util.Locale
import kotlin.math.abs

private const val CLOUD_SOURCE_PREFIX = "Cloud collaboratif"
private const val MAX_CLOUD_RESULT_AGE_MS = 24 * 60 * 60 * 1000L
private const val MAX_EVENT_LOOKBACK_MS = 48 * 60 * 60 * 1000L
private const val MAX_CLOCK_SKEW_MS = 10 * 60 * 1000L
private const val MAX_TEXT_FIELD_CHARS = 1_200
private const val MAX_SCENARIO_CHARS = 2_400
private const val MAX_DOCUMENT_CHARS = 9_000

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

data class CloudMergeResult(
    val predictionsToUpsert: List<PredictionEntity>,
    val acceptedCloudCount: Int,
    val rejectedCloudCount: Int,
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
)

fun PredictionEntity.toCloudSharedResult(
    appVersion: String,
    deviceId: String,
    now: Long,
): CloudSharedResult? {
    if (eventId.isBlank() || sportKey.isBlank() || competitionName.isBlank()) return null
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
    ).takeIf { it.isCoherent(now) }
}

fun UpcomingEventEntity.toCloudSharedCalendarEvent(
    appVersion: String,
    deviceId: String,
    now: Long,
): CloudSharedCalendarEvent? {
    if (id.isBlank() || sportKey.isBlank() || competitionName.isBlank()) return null
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

fun CloudSharedResult.isCoherent(now: Long): Boolean {
    if (eventId.isBlank() || sport.isBlank() || competition.isBlank() || eventName.isBlank()) return false
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
    if (eventDate <= 0L || updatedAt <= 0L || expiresAt <= 0L) return false
    if (updatedAt > now + MAX_CLOCK_SKEW_MS) return false
    if (eventDate < now - MAX_EVENT_LOOKBACK_MS) return false
    if (expiresAt <= now) return false
    return toFirestoreMap().stringPayloadSize() <= MAX_DOCUMENT_CHARS
}

fun CloudSharedResult.toPredictionEntity(local: PredictionEntity? = null): PredictionEntity {
    val source = if (sourceName.startsWith(CLOUD_SOURCE_PREFIX, ignoreCase = true)) {
        sourceName
    } else {
        "$CLOUD_SOURCE_PREFIX · ${sourceName.ifBlank { appVersion }}"
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
        betclicOdds = local?.betclicOdds ?: 0.0,
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
    )
}

fun mergeCloudResults(
    localPredictions: List<PredictionEntity>,
    cloudResults: List<CloudSharedResult>,
    now: Long,
): CloudMergeResult {
    val localsByEvent = localPredictions.associateBy { it.cloudIdentityKey() }
    var rejected = 0
    val accepted = cloudResults
        .asSequence()
        .filter { cloud ->
            val valid = cloud.isCoherent(now)
            if (!valid) rejected += 1
            valid
        }
        .sortedByDescending { it.updatedAt }
        .distinctBy { it.cloudIdentityKey() }
        .mapNotNull { cloud ->
            val local = localsByEvent[cloud.cloudIdentityKey()]
            if (shouldUseCloudResult(local, cloud, now)) cloud.toPredictionEntity(local) else null
        }
        .toList()
    return CloudMergeResult(
        predictionsToUpsert = accepted,
        acceptedCloudCount = accepted.size,
        rejectedCloudCount = rejected,
    )
}

fun shouldUseCloudResult(local: PredictionEntity?, cloud: CloudSharedResult, now: Long): Boolean {
    if (!cloud.isCoherent(now)) return false
    if (local == null) return true
    if (local.selection.isBlank() || local.market.isBlank()) return true
    if (local.commenceTime < now - MAX_EVENT_LOOKBACK_MS) return true
    return cloud.updatedAt > local.sourceLastUpdate
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
    )
}

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
    )
}.getOrNull()

fun cloudDocumentIdFor(eventId: String): String =
    eventId.trim()
        .ifBlank { "unknown-event" }
        .replace(Regex("[^A-Za-z0-9_-]+"), "_")
        .trim('_')
        .take(180)
        .ifBlank { "unknown-event" }

private fun PredictionEntity.cloudIdentityKey(): String =
    listOf(eventId, sportKey.substringBefore('/'), homeTeam, awayTeam, commenceTime.roundToMinute())
        .joinToString("|")
        .lowercase(Locale.US)

private fun CloudSharedResult.cloudIdentityKey(): String =
    listOf(eventId, sport, homeTeam, awayTeam, eventDate.roundToMinute())
        .joinToString("|")
        .lowercase(Locale.US)

private fun Long.roundToMinute(): Long = this / 60_000L

private fun String.trimCloudText(max: Int): String =
    replace(Regex("\\s+"), " ").trim().take(max)

private fun Double.formatCloudDouble(): String =
    if (isFinite()) String.format(Locale.US, "%.4f", this) else "0.0000"

private fun Double.isCloudProbability(): Boolean =
    isFinite() && this >= 0.0 && this <= 1.0

private fun Map<String, Any>.stringPayloadSize(): Int =
    values.sumOf { value -> if (value is String) value.length else 16 }

private fun Map<String, Any?>.stringValue(key: String): String =
    (this[key] as? String).orEmpty().trimCloudText(MAX_SCENARIO_CHARS)

private fun Map<String, Any?>.longValue(key: String): Long =
    when (val value = this[key]) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

private fun Map<String, Any?>.intValue(key: String): Int =
    longValue(key).toInt()

private fun Map<String, Any?>.doubleValue(key: String): Double =
    when (val value = this[key]) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }.takeIf { it.isFinite() && abs(it) < 1_000_000.0 } ?: 0.0
