package com.soliano.betvalueanalyzer.domain

import com.soliano.betvalueanalyzer.data.local.LiveEventEntity

object LiveWindowPolicy {
    const val PRE_START_WINDOW_MS: Long = 30L * 60L * 1000L
    const val RECENT_FINISH_WINDOW_MS: Long = 30L * 60L * 1000L

    fun shouldShow(event: LiveEventEntity, now: Long): Boolean =
        liveState(event, now).visibleInLiveTab

    fun liveState(event: LiveEventEntity, now: Long): LiveEventStateResolution =
        LiveEventStateResolver.resolve(event, now)

    fun startsSoon(commenceTime: Long, now: Long): Boolean =
        commenceTime in now..(now + PRE_START_WINDOW_MS)

    fun finishedRecently(event: LiveEventEntity, now: Long): Boolean {
        val state = liveState(event, now).state
        return state == LiveEventState.Finished || state == LiveEventState.ResultConfirmed
    }

    fun finishedRecentlyBySchedule(sportKey: String, commenceTime: Long, now: Long): Boolean {
        val estimatedEnd = commenceTime + LiveEventStateResolver.estimatedDurationMs(sportKey.substringBefore('/'))
        return estimatedEnd in (now - RECENT_FINISH_WINDOW_MS)..now
    }

    fun monitorWindowLabel(): String = "live, terminé <30 min ou départ <30 min"
}
