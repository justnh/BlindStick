package com.gaythongminh.app.util

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gaythongminh.app.MainActivity
import com.gaythongminh.app.R
import com.gaythongminh.app.data.coordText
import com.gaythongminh.app.data.formatTime
import com.gaythongminh.app.data.mapsLink

object Notifier {
    const val CH_SERVICE = "service"
    const val CH_FALL = "fall_alert"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Dịch vụ chạy nền", NotificationManager.IMPORTANCE_LOW)
        )
        val fall = NotificationChannel(CH_FALL, "Cảnh báo té ngã", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(fall)
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun service(ctx: Context, text: String): Notification =
        NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_cane)
            .setContentTitle("Gậy Thông Minh")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openApp(ctx))
            .build()

    @SuppressLint("MissingPermission")
    fun updateService(ctx: Context, id: Int, text: String) {
        if (!Perms.hasNotification(ctx)) return
        NotificationManagerCompat.from(ctx).notify(id, service(ctx, text))
    }

    @SuppressLint("MissingPermission")
    fun showFall(ctx: Context, id: Int, caneEmail: String, time: Long, lat: Double?, lon: Double?) {
        if (!Perms.hasNotification(ctx)) return
        val place = if (lat != null && lon != null) "Vị trí: ${coordText(lat, lon)}\n${mapsLink(lat, lon)}" else "Chưa có vị trí"
        val text = "$caneEmail bị ngã lúc ${formatTime(time)}"
        val b = NotificationCompat.Builder(ctx, CH_FALL)
            .setSmallIcon(R.drawable.ic_stat_cane)
            .setContentTitle("⚠ Người thân bị ngã!")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$place"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
        if (lat != null && lon != null) {
            val view = PendingIntent.getActivity(
                ctx, id,
                Intent(Intent.ACTION_VIEW, Uri.parse(mapsLink(lat, lon))),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.addAction(0, "Mở Google Maps", view)
        }
        NotificationManagerCompat.from(ctx).notify(id, b.build())
    }
}
