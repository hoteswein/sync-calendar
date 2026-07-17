package com.hotesv.synccalendar

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: "Напоминание"
        val id = intent.getStringExtra("id") ?: "0"
        DebugLog.add(context, "AlarmReceiver.onReceive СРАБОТАЛ: id=${id.take(6)} text=\"${text.take(20)}\"")

        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val locked = km.isKeyguardLocked
        val canFsi = if (android.os.Build.VERSION.SDK_INT >= 34) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).canUseFullScreenIntent()
        } else {
            true
        }
        DebugLog.add(context, "состояние в момент звонка: экран заблокирован=$locked, canUseFullScreenIntent=$canFsi")

        try {
            handleAlarm(context, id, text)
            DebugLog.add(context, "AlarmReceiver: уведомление успешно отправлено для id=${id.take(6)}")
        } catch (e: Exception) {
            DebugLog.add(context, "AlarmReceiver: ОШИБКА ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun handleAlarm(context: Context, id: String, text: String) {
        val channelId = NotificationChannelHelper.ensureChannel(context)

        // полноэкранная активность поверх блокировки — на неё же ведёт
        // и обычный тап по уведомлению, и кнопка "Отложить на..."
        val popupIntentBase = Intent(context, ReminderAlarmActivity::class.java).apply {
            putExtra("id", id)
            putExtra("text", text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, ("fsi_$id").hashCode(), popupIntentBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeMenuIntent = Intent(popupIntentBase).putExtra("open_snooze_menu", true)
        val snoozeMenuPendingIntent = PendingIntent.getActivity(
            context, ("snoozemenu_$id").hashCode(), snoozeMenuIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, ("dismiss_$id").hashCode(),
            Intent(context, AlarmActionReceiver::class.java).apply {
                action = AlarmActionReceiver.ACTION_DISMISS
                putExtra("id", id)
                putExtra("text", text)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze5PendingIntent = PendingIntent.getBroadcast(
            context, ("snooze5_$id").hashCode(),
            Intent(context, AlarmActionReceiver::class.java).apply {
                action = AlarmActionReceiver.ACTION_SNOOZE
                putExtra("id", id)
                putExtra("text", text)
                putExtra(AlarmActionReceiver.EXTRA_MINUTES, 5)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(R.drawable.ic_alarm, context.getString(R.string.dismiss), dismissPendingIntent)
            .addAction(R.drawable.ic_alarm, context.getString(R.string.snooze_5min), snooze5PendingIntent)
            .addAction(R.drawable.ic_alarm, context.getString(R.string.snooze_pick), snoozeMenuPendingIntent)

        // Раньше здесь была проверка canUseFullScreenIntent() перед вызовом
        // setFullScreenIntent — убрал её как условие: по документации Android,
        // если разрешения нет, система просто покажет обычное уведомление
        // без попапа, а не упадёт. Так надёжнее, чем полагаться на то, что
        // сам метод canUseFullScreenIntent() всегда точно отражает реальность
        // на конкретном устройстве/прошивке.

        NotificationManagerCompat.from(context).notify(id.hashCode(), builder.build())
    }
}
