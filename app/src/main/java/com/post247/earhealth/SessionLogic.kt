package com.post247.earhealth

/**
 * Pure logic for the 60/60 session tracker. State lives in [Store] so it
 * survives process death; elapsed time is always derived from wall-clock
 * timestamps rather than accumulated ticks.
 */
object SessionLogic {

    /**
     * Active (non-paused) milliseconds in the current session.
     * Returns null when no session is running.
     */
    fun activeMs(s: Store, nowMs: Long): Long? {
        if (!s.sessionRunning) return null
        var paused = s.pausedAccumMs
        if (s.sessionPaused) paused += nowMs - s.lastPauseStartMs
        return (nowMs - s.sessionStartEpochMs - paused).coerceAtLeast(0L)
    }

    fun format(ms: Long): String {
        val totalMin = ms / 60_000L
        val h = totalMin / 60
        val m = totalMin % 60
        val sec = (ms % 60_000L) / 1_000L
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec)
        else "%02d:%02d".format(m, sec)
    }
}