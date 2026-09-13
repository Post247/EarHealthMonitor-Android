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
 * Android does not allow silent background volume control. A call to
 * AudioManager.setStreamVolume() only has effect while the app is visible or
 * while this "while-in-use" foreground service is running, so the cap is
 * implemented as a mediaPlayback foreground service with a persistent
 * notification — exactly the trade-off discovered during research.
 */
class VolumeCapService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var audioManager: AudioManager? = null
    private lateinit var store: Store
    private var running = false

    private val volumeCallback = object : AudioManager.VolumeCallback() {
        override fun onVolumeChanged(stream: Int, flags: Int) {
            if (stream == AudioManager.STREAM_MUSIC) {
                enforceCap()
            }
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
            audioManager?.registerAudioVolumeCallback(volumeCallback, handler)
            running = true
            Log.i(TAG, "Volume cap service started")
        }
        enforceCap()
        return START_STICKY
    }

    override fun onDestroy() {
        audioManager?.unregisterAudioVolumeCallback(volumeCallback)
        running = false
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
        const val ACTION_CONTROL = "com.post247.earhealth.control"
    }
}