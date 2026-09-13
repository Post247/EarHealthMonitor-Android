package com.post247.earhealth

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private var audioManager: AudioManager? = null

    private lateinit var tvVolumePct: TextView
    private lateinit var tvServiceState: TextView
    private lateinit var seekVolume: SeekBar
    private lateinit var seekCap: SeekBar
    private lateinit var tvVolumeCap: TextView
    private lateinit var tvSessionState: TextView
    private lateinit var tvElapsed: TextView
    private lateinit var btnSession: Button
    private lateinit var btnPause: Button
    private lateinit var tvToday: TextView
    private lateinit var tvWeek: TextView
    private lateinit var tvSessions: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = Store(applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Alerts.ensureChannels(this)

        tvVolumePct = findViewById(R.id.tvVolumePct)
        tvServiceState = findViewById(R.id.tvServiceState)
        seekVolume = findViewById(R.id.seekVolume)
        seekCap = findViewById(R.id.seekCap)
        tvVolumeCap = findViewById(R.id.tvVolumeCap)
        tvSessionState = findViewById(R.id.tvSessionState)
        tvElapsed = findViewById(R.id.tvElapsed)
        btnSession = findViewById(R.id.btnSession)
        btnPause = findViewById(R.id.btnPause)
        tvToday = findViewById(R.id.tvToday)
        tvWeek = findViewById(R.id.tvWeek)
        tvSessions = findViewById(R.id.tvSessions)

        seekCap.progress = store.capPercent
        tvVolumeCap.text = "Cap: ${store.capPercent}%"
        seekCap.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, v: Int, fromUser: Boolean) {
                val progress = v.coerceIn(1, 100)
                if (sb != null && sb.progress != progress) sb.progress = progress
                store.capPercent = progress
                tvVolumeCap.text = "Cap: ${store.capPercent}%"
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnSession.setOnClickListener {
            if (store.sessionRunning) stopSession() else startSession()
            updateSessionButtons()
        }
        btnPause.setOnClickListener {
            togglePause()
            updateSessionButtons()
        }

        // The cap service is started on every resume, so it is effectively
        // always on while the app has been opened.
        tvServiceState.text = "Cap active"
        tvServiceState.setTextColor(ContextCompat.getColor(this, R.color.accent))

        refreshVolumeDisplay()
        requestNotificationPermissionIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        handler.post(tickRunnable)
        updateSessionButtons()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(tickRunnable)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (granted != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The cap is an always-on background service; launching it from the
        // foreground guarantees it gets while-in-use capabilities.
        ContextCompat.startForegroundService(this, Intent(this, VolumeCapService::class.java))
    }

    // ---- volume display ----

    private fun refreshVolumeDisplay() {
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val pct = (cur * 100 + max / 2) / max
        seekVolume.progress = pct
        tvVolumePct.text = "Media volume: $pct%"
    }

    // ---- session ----

    private fun startSession() {
        val now = System.currentTimeMillis()
        val today = java.time.LocalDate.now().toString()
        if (store.lastAlertDay != today) {
            store.dailyAlertSent = false
            store.lastAlertDay = today
        }
        store.sessionRunning = true
        store.sessionPaused = false
        store.sessionStartEpochMs = now
        store.pausedAccumMs = 0L
        store.lastPauseStartMs = now
        store.continuousAlertSent = false
        store.loggedActiveMs = 0L
        store.newSessionStarted()
        updateSessionButtons()
    }

    private fun stopSession() {
        val now = System.currentTimeMillis()
        val active = SessionLogic.activeMs(store, now) ?: 0L
        if (active > store.loggedActiveMs) {
            store.addActiveMinutes(active - store.loggedActiveMs)
            store.loggedActiveMs = active
        }
        store.sessionRunning = false
        store.sessionPaused = false
        store.pausedAccumMs = 0L
        store.loggedActiveMs = 0L
    }

    private fun togglePause() {
        if (!store.sessionRunning) return
        val now = System.currentTimeMillis()
        if (store.sessionPaused) {
            store.pausedAccumMs += now - store.lastPauseStartMs
            store.sessionPaused = false
            store.loggedActiveMs = SessionLogic.activeMs(store, now) ?: store.loggedActiveMs
        } else {
            store.sessionPaused = true
            store.lastPauseStartMs = now
        }
    }

    private fun tick() {
        refreshVolumeDisplay()

        if (!store.sessionRunning) {
            tvSessionState.text = "Idle"
            tvSessionState.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            tvElapsed.text = "00:00"
            updateHistoryDisplay()
            updateSessionButtons()
            return
        }

        val now = System.currentTimeMillis()
        val active = SessionLogic.activeMs(store, now) ?: 0L

        // Crash-safe accrual: only log the *delta* since the last checkpoint,
        // and persist the checkpoint so a killed process's gap is recovered.
        if (active > store.loggedActiveMs) {
            store.addActiveMinutes(active - store.loggedActiveMs)
            store.loggedActiveMs = active
        }

        tvElapsed.text = SessionLogic.format(active)
        tvSessionState.text = if (store.sessionPaused) "Paused" else "Listening"
        tvSessionState.setTextColor(
            ContextCompat.getColor(this, if (store.sessionPaused) R.color.text_muted else R.color.accent)
        )

        if (active >= 60 * 60_000L && !store.continuousAlertSent) {
            Alerts.alert(
                this,
                "60 minutes — take a break",
                "Your ears have been at it for 60 continuous minutes. Rest at least 10."
            )
            store.continuousAlertSent = true
        }

        val todayMin = store.todayMinutes() + active / 60_000L
        val dayKey = java.time.LocalDate.now().toString()
        if (todayMin >= store.dailyTargetMinutes && !store.dailyAlertSent && store.lastAlertDay == dayKey) {
            Alerts.alert(
                this,
                "Daily target reached",
                "You've passed your ${store.dailyTargetMinutes}-minute daily target. Give your ears a long break."
            )
            store.dailyAlertSent = true
        }

        updateHistoryDisplay()
        updateSessionButtons()
    }

    private fun updateHistoryDisplay() {
        tvToday.text = "Today: ${store.todayMinutes()} min"
        tvWeek.text = "This week: ${store.weekMinutes()} min"
        tvSessions.text = "Sessions today: ${store.todaySessions()}"
    }

    private fun updateSessionButtons() {
        btnSession.text = if (store.sessionRunning) "Stop" else "Start"
        btnPause.isEnabled = store.sessionRunning
        btnPause.text = if (store.sessionPaused) "Resume" else "Pause"
    }

    companion object {
        private const val REQ_NOTIF = 100
    }
}