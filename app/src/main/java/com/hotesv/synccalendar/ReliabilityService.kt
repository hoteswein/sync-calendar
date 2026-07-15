package com.hotesv.synccalendar

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationCompat

/** Единственная задача этой службы — существовать как foreground service,
 *  чтобы процесс приложения имел куда более высокий приоритет в глазах
 *  системы и не убивался агрессивным управлением памятью (характерно
 *  для MIUI/HyperOS и подобных прошивок). Сама логика напоминаний
 *  по-прежнему идёт через AlarmManager/AlarmReceiver — служба ничего
 *  не проверяет сама, только держит процесс живым. Это тот же принцип,
 *  на котором держится надёжность автоматизаторов вроде MacroDroid —
 *  отсюда и их всегда видимый значок в уведомлениях. */
class ReliabilityService : Service() {

    companion object {
        const val CHANNEL_ID = "reliability_service"
        const val NOTIFICATION_ID = 42
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID, "Служба надёжности", NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null
}
