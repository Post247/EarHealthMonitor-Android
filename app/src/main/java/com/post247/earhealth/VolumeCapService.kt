package com.post247.earhealth

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * Foreground service that enforces the media-volume cap.
 *
 * Android offers no public volume-change callback, so the cap is enforced by a
 * lightweight poll of AudioManager.getStreamVolume(). And because Android's
 * background audio hardening (Android 17 / API 37) makes setStreamVolume() fail
 * silently when a background process has no while-in-use capabilities, the cap
 * runs as a mediaPlayback foreground service with a persistent notification —
 * the trade-off discovered during research.
 */
class VolumeCapService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var audioManager: AudioManager? = null
    private lateinit var store: Store
    private var running = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            enforceCap()
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = Store(applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            Alerts.ensureChannels(this)
            startForeground(Alerts.SERVICE_ID, Alerts.serviceNotification(this))
            running = true
            handler.post(pollRunnable)
            Log.i(TAG, "Volume cap service started")
        }
        enforceCap()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(pollRunnable)
        Log.i(TAG, "Volume cap service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Clamps the current media volume down to the configured cap (percent → index). */
    private fun enforceCap() {
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        val capIndex = (store.capPercent * max + 50) / 100
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current > capIndex) {
            Log.i(TAG, "Clamping volume $current -> $capIndex (cap ${store.capPercent}%)")
            am.setStreamVolume(AudioManager.STREAM_MUSIC, capIndex, 0)
        }
    }

    companion object {
        private const val TAG = "VolumeCapService"
        private const val POLL_MS = 700L
    }
}