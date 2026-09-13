package com.post247.earhealth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color

/**
 * Notification helpers. Two channels:
 *  - SERVICE_CHANNEL: the mandatory foreground-service notification for the volume cap.
 *  - ALERT_CHANNEL: 60/60 session reminders.
 */
object Alerts {

    const val SERVICE_CHANNEL = "volume_cap_service"
    const val ALERT_CHANNEL = "session_alerts"

    const val SERVICE_ID = 1001
    const val ALERT_ID = 1002

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(SERVICE_CHANNEL, ctx.getString(R.string.service_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = ctx.getString(R.string.service_channel_desc)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, ctx.getString(R.string.session_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.session_channel_desc)
                setShowBadge(true)
            }
        )
    }

    fun serviceNotification(ctx: Context): Notification {
        val openIntent = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(ctx, SERVICE_CHANNEL)
            .setContentTitle("Volume cap active")
            .setContentText("Capping media volume while this notification is visible.")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    fun alert(ctx: Context, title: String, body: String, color: Int = Color.parseColor("#34D399")) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openIntent = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, ALERT_CHANNEL)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(openIntent)
            .setColor(color)
            .setAutoCancel(true)
            .build()
        nm.notify(ALERT_ID, n)
    }
}