package com.soliano.betvalueanalyzer.domain

import com.soliano.betvalueanalyzer.data.local.LiveEventEntity

enum class LiveEventState {
    Upcoming,
    Soon,
    Live,
    Finished,
    ResultConfirmed,
    Archived,
}

data class LiveEventStateResolution(
    val state: LiveEventState,
    val estimatedEndEpoch: Long,
    val visibleInLiveTab: Boolean,
) {
    val isFinalState: Boolean
        get() = state == LiveEventState.Finished ||
            state == LiveEventState.ResultConfirmed ||
            state == LiveEventState.Archived
}

object LiveEventStateResolver {
    fun resolve(event: LiveEventEntity, now: Long): LiveEventStateResolution {
        val sport = event.sportKey.substringBefore('/')
        val estimatedEnd = event.commenceTime + estimatedDurationMs(sport)
        val completed = event.looksCompleted()
        val confirmed = event.looksResultConfirmed()
        val state = when {
            event.isLive && !completed -> LiveEventState.Live
            confirmed && estimatedEnd in (now - LiveWindowPolicy.RECENT_FINISH_WINDOW_MS)..now ->
                LiveEventState.ResultConfirmed
            completed && estimatedEnd in (now - LiveWindowPolicy.RECENT_FINISH_WINDOW_MS)..now ->
                LiveEventState.Finished
            completed || estimatedEnd < now - LiveWindowPolicy.RECENT_FINISH_WINDOW_MS ->
                LiveEventState.Archived
            LiveWindowPolicy.startsSoon(event.commenceTime, now) -> LiveEventState.Soon
            event.commenceTime > now -> LiveEventState.Upcoming
            else -> LiveEventState.Archived
        }
        return LiveEventStateResolution(
            state = state,
            estimatedEndEpoch = estimatedEnd,
            visibleInLiveTab = state == LiveEventState.Soon ||
                state == LiveEventState.Live ||
                state == LiveEventState.Finished ||
                state == LiveEventState.ResultConfirmed,
        )
    }

    fun displayLabel(state: LiveEventState): String = when (state) {
        LiveEventState.Upcoming -> "À venir"
        LiveEventState.Soon -> "Bientôt"
        LiveEventState.Live -> "En direct"
        LiveEventState.Finished -> "Terminé"
        LiveEventState.ResultConfirmed -> "Résultat confirmé"
        LiveEventState.Archived -> "Archivé"
    }

    fun estimatedDurationMs(sport: String): Long = when (sport) {
        "soccer", "football", "rugby", "hockey", "handball" -> 2L * 60L * 60L * 1000L
        "basketball", "volleyball" -> 2L * 60L * 60L * 1000L
        "baseball", "tennis", "mma", "boxing" -> 3L * 60L * 60L * 1000L
        "racing", "nascar" -> 2L * 60L * 60L * 1000L
        "cycling", "golf" -> 5L * 60L * 60L * 1000L
        "athletics" -> 90L * 60L * 1000L
        else -> 2L * 60L * 60L * 1000L
    }

    private fun LiveEventEntity.looksCompleted(): Boolean {
        val raw = listOf(statusState, statusDescription)
            .joinToString(" ")
            .lowercase()
        return raw.contains("post") ||
            raw.contains("final") ||
            raw.contains("terminé") ||
            raw.contains("termine") ||
            raw.contains("finished") ||
            raw.contains("full time") ||
            raw == "ft" ||
            raw.contains(" ft ")
    }

    private fun LiveEventEntity.looksResultConfirmed(): Boolean {
        if (!looksCompleted()) return false
        val hasScore = homeScore != null && awayScore != null
        val hasClassification = statSummary.contains("Top 3", ignoreCase = true) ||
            statSummary.contains("Classement", ignoreCase = true) ||
            resultSummaryText().contains("Top 3", ignoreCase = true)
        return hasScore || hasClassification
    }

    private fun LiveEventEntity.resultSummaryText(): String =
        listOf(statusDescription, displayClock, statSummary, scenarios)
            .joinToString(" ")
}
