package com.hotesv.synccalendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri

/** Канал уведомлений и его звук.
 *
 *  На Android 8+ звук канала фиксируется один раз при создании, поэтому
 *  id канала строится на основе конкретного звука (его uri) — при смене
 *  звука это всегда новый, ранее не использовавшийся id.
 *
 *  С индивидуальной мелодией на напоминание канал строится не только по
 *  глобальному звуку (Prefs), но по любому переданному uri — вызывающий
 *  код (AlarmReceiver) сам решает, какой звук "действующий" для
 *  конкретного напоминания (свой файл в sounds/ или общий). */
object NotificationChannelHelper {
    private const val PREFIX = "reminders_v"
    private const val SILENT_ID = "reminders_silent"

    fun channelIdForUri(soundUriString: String?): String =
        PREFIX + (soundUriString?.hashCode() ?: 0)

    fun channelIdFor(context: Context): String = channelIdForUri(Prefs.getSoundUri(context))

    fun ensureChannel(context: Context, soundUriString: String?): String {
        val id = channelIdForUri(soundUriString)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(buildChannel(id, soundUriString))
        }
        return id
    }

    /** Без звука канала вообще — для случаев, когда попап (Activity или
     *  overlay) и так покажется сам немедленно, и звук берёт на себя наш
     *  собственный MediaPlayer в нём. Не даём звуку сыграть дважды почти
     *  одновременно (эффект "эха" на десятые доли секунды). */
    fun ensureSilentChannel(context: Context): String {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(SILENT_ID) == null) {
            manager.createNotificationChannel(buildSilentChannel())
        }
        return SILENT_ID
    }

    fun onSoundChanged(context: Context, previousChannelId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.deleteNotificationChannel(previousChannelId)
        ensureChannel(context, Prefs.getSoundUri(context))
    }

    private fun buildChannel(id: String, soundUriString: String?): NotificationChannel {
        val soundUri: Uri = soundUriString?.let { Uri.parse(it) }
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

    private fun buildSilentChannel(): NotificationChannel {
        return NotificationChannel(SILENT_ID, "Напоминания (звук через попап)", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableLights(true)
            enableVibration(true)
        }
    }
}
