package com.hotesv.synccalendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri

/** Канал уведомлений и его звук.
 *
 *  ВАЖНО: на Android 8+ звук канала фиксируется один раз при создании.
 *  Удаление канала и создание нового ПОД ТЕМ ЖЕ id не помогает — система
 *  документированно "восстанавливает" старый канал со старыми
 *  настройками вместо применения новых (это была причина бага: звук не
 *  менялся). Поэтому здесь id канала зависит от самого звука — при
 *  смене звука это всегда новый, ранее не использовавшийся id. */
object NotificationChannelHelper {
    private const val PREFIX = "reminders_v"

    fun channelIdFor(context: Context): String {
        val key = Prefs.getSoundUri(context)?.hashCode() ?: 0
        return PREFIX + key
    }

    /** Создаёт канал под текущим (на основе текущего звука) id, если его ещё нет. */
    fun ensureChannel(context: Context): String {
        val id = channelIdFor(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(buildChannel(context, id))
        }
        return id
    }

    /** Вызывать сразу после того, как пользователь выбрал новый звук
     *  (Prefs уже обновлён к этому моменту) — создаёт канал под новым id
     *  и подчищает канал под старым id, чтобы не копились "хвосты". */
    fun onSoundChanged(context: Context, previousChannelId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.deleteNotificationChannel(previousChannelId)
        ensureChannel(context)
    }

    private fun buildChannel(context: Context, id: String): NotificationChannel {
        val soundUri: Uri = Prefs.getSoundUri(context)?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        return NotificationChannel(id, "Напоминания", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(soundUri, attrs)
            enableLights(true)
            enableVibration(true)
        }
    }
}
