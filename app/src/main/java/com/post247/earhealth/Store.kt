package com.post247.earhealth

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * Persists daily listening minutes, session counts and user settings.
 * Mirrors the desktop app's config.json + usage_history.json.
 */
class Store(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ear_health", Context.MODE_PRIVATE)

    private var cachedStarts: MutableMap<LocalDate, Long> = loadIntMap(prefs, KEY_MINUTES)
    private var cachedSessions: MutableMap<LocalDate, Int> = loadIntMap(prefs, KEY_SESSIONS)

    // ---- settings ----

    var capPercent: Int
        get() = prefs.getInt(KEY_CAP, 60)
        set(v) = prefs.edit().putInt(KEY_CAP, v.coerceIn(1, 100)).apply()

    val dailyTargetMinutes: Int get() = 60

    // ---- session persistence (survives process death) ----

    var sessionRunning: Boolean
        get() = prefs.getBoolean(KEY_RUNNING, false)
        set(v) = prefs.edit().putBoolean(KEY_RUNNING, v).apply()

    var sessionPaused: Boolean
        get() = prefs.getBoolean(KEY_PAUSED, false)
        set(v) = prefs.edit().putBoolean(KEY_PAUSED, v).apply()

    var sessionStartEpochMs: Long
        get() = prefs.getLong(KEY_START_MS, 0L)
        set(v) = prefs.edit().putLong(KEY_START_MS, v).apply()

    var pausedAccumMs: Long
        get() = prefs.getLong(KEY_PAUSED_ACCUM, 0L)
        set(v) = prefs.edit().putLong(KEY_PAUSED_ACCUM, v).apply()

    var lastPauseStartMs: Long
        get() = prefs.getLong(KEY_PAUSE_START, 0L)
        set(v) = prefs.edit().putLong(KEY_PAUSE_START, v).apply()

    var continuousAlertSent: Boolean
        get() = prefs.getBoolean(KEY_ALERT_SENT, false)
        set(v) = prefs.edit().putBoolean(KEY_ALERT_SENT, v).apply()

    var dailyAlertSent: Boolean
        get() = prefs.getBoolean(KEY_DAILY_ALERT, false)
        set(v) = prefs.edit().putBoolean(KEY_DAILY_ALERT, v).apply()

    var lastAlertDay: String?
        get() = prefs.getString(KEY_ALERT_DAY, null)
        set(v) = prefs.edit().putString(KEY_ALERT_DAY, v).apply()

    // ---- history ----

    fun todayMinutes(): Int = cachedStarts[LocalDate.now()]?.let { (it / 60_000L).toInt() } ?: 0

    fun weekMinutes(): Int {
        var total = 0L
        val today = LocalDate.now()
        for (i in 0 until 7) {
            total += cachedStarts[today.minusDays(i.toLong())] ?: 0L
        }
        return (total / 60_000L).toInt()
    }

    fun todaySessions(): Int = cachedSessions[LocalDate.now()] ?: 0

    /** Accumulates active (non-paused) session time into daily history. */
    fun addActiveMinutes(ms: Long) {
        if (ms <= 0) return
        val day = LocalDate.now()
        val cur = cachedStarts[day] ?: 0L
        cachedStarts[day] = cur + ms
        persist(cachedStarts, KEY_MINUTES)
    }

    /** Increments the session count for today (called once per session start). */
    fun newSessionStarted() {
        val day = LocalDate.now()
        cachedSessions[day] = (cachedSessions[day] ?: 0) + 1
        persist(cachedSessions, KEY_SESSIONS)
    }

    private fun persist(map: Map<LocalDate, Int>, key: String) {
        val sb = StringBuilder()
        map.forEach { (date, v) -> sb.append(date).append('=').append(v).append('\n') }
        prefs.edit().putString(key, sb.toString()).apply()
    }

    companion object {
        private const val KEY_CAP = "cap_percent"
        private const val KEY_RUNNING = "session_running"
        private const val KEY_PAUSED = "session_paused"
        private const val KEY_START_MS = "session_start_ms"
        private const val KEY_PAUSED_ACCUM = "paused_accum_ms"
        private const val KEY_PAUSE_START = "pause_start_ms"
        private const val KEY_ALERT_SENT = "alert_sent"
        private const val KEY_DAILY_ALERT = "daily_alert_sent"
        private const val KEY_ALERT_DAY = "alert_day"
        private const val KEY_MINUTES = "minutes_by_day"
        private const val KEY_SESSIONS = "sessions_by_day"

        private fun loadIntMap(p: SharedPreferences, key: String): MutableMap<LocalDate, Int> {
            val raw = p.getString(key, null)
            val map = mutableMapOf<LocalDate, Int>()
            if (raw != null) {
                raw.split("\n").forEach { line ->
                    val parts = line.split("=")
                    if (parts.size == 2) {
                        runCatching {
                            map[LocalDate.parse(parts[0])] = parts[1].toInt()
                        }
                    }
                }
            }
            return map
        }
    }
}