package com.soliano.betvalueanalyzer.domain

import com.soliano.betvalueanalyzer.data.local.LiveEventEntity

object LiveWindowPolicy {
    const val PRE_START_WINDOW_MS: Long = 30L * 60L * 1000L
    const val RECENT_FINISH_WINDOW_MS: Long = 30L * 60L * 1000L

    fun shouldShow(event: LiveEventEntity, now: Long): Boolean =
        event.isLive || startsSoon(event.commenceTime, now) || finishedRecently(event, now)

    fun startsSoon(commenceTime: Long, now: Long): Boolean =
        commenceTime in now..(now + PRE_START_WINDOW_MS)

    fun finishedRecently(event: LiveEventEntity, now: Long): Boolean {
        if (!event.looksCompleted()) return false
        val estimatedEnd = event.commenceTime + estimatedDurationMs(event.sportKey.substringBefore('/'))
        return estimatedEnd in (now - RECENT_FINISH_WINDOW_MS)..now
    }

    fun finishedRecentlyBySchedule(sportKey: String, commenceTime: Long, now: Long): Boolean {
        val estimatedEnd = commenceTime + estimatedDurationMs(sportKey.substringBefore('/'))
        return estimatedEnd in (now - RECENT_FINISH_WINDOW_MS)..now
    }

    fun monitorWindowLabel(): String = "live, terminé <30 min ou départ <30 min"

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

    private fun estimatedDurationMs(sport: String): Long = when (sport) {
        "soccer", "football", "rugby", "hockey", "handball" -> 2L * 60L * 60L * 1000L
        "basketball", "volleyball" -> 2L * 60L * 60L * 1000L
        "baseball", "cricket", "tennis", "mma", "boxing", "darts" -> 3L * 60L * 60L * 1000L
        "racing", "nascar" -> 2L * 60L * 60L * 1000L
        "cycling", "golf" -> 5L * 60L * 60L * 1000L
        "athletics" -> 90L * 60L * 1000L
        else -> 2L * 60L * 60L * 1000L
    }
}
