package com.hotesv.synccalendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri

/** Канал уведомлений и его звук.
 *
 *  На Android 8+ звук канала фиксируется один раз при создании и не может
 *  быть изменён у существующего канала — поэтому при смене звука в
 *  настройках нужно удалить канал и создать заново (recreateChannel),
 *  а не просто обновить его. */
object NotificationChannelHelper {
    const val CHANNEL_ID = "reminders"

    /** Создаёт канал, если его ещё нет — с текущим звуком из настроек. */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(buildChannel(context))
        }
    }

    /** Вызывать сразу после того, как пользователь выбрал новый звук. */
    fun recreateChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.deleteNotificationChannel(CHANNEL_ID)
        manager.createNotificationChannel(buildChannel(context))
    }

    private fun buildChannel(context: Context): NotificationChannel {
        val soundUri: Uri = Prefs.getSoundUri(context)?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        return NotificationChannel(CHANNEL_ID, "Напоминания", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(soundUri, attrs)
            enableLights(true)
            enableVibration(true)
        }
    }
}
