package com.hotesv.synccalendar

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        NotificationChannelHelper.ensureChannel(context)

        val text = intent.getStringExtra("text") ?: "Напоминание"
        val id = intent.getStringExtra("id") ?: "0"

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

        val builder = NotificationCompat.Builder(context, NotificationChannelHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_alarm, context.getString(R.string.dismiss), dismissPendingIntent)
            .addAction(R.drawable.ic_alarm, context.getString(R.string.snooze_5min), snooze5PendingIntent)
            .addAction(R.drawable.ic_alarm, context.getString(R.string.snooze_pick), snoozeMenuPendingIntent)

        // На Android 14+ полноэкранный интент может быть не разрешён (спец. доступ,
        // авто-выдаётся только "будильникам/звонилкам" через Google Play — при
        // сайдлоаде это ограничение Play Store не применяется, но проверяем честно).
        val canFullScreen =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.canUseFullScreenIntent()
            } else {
                true
            }

        if (canFullScreen) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        NotificationManagerCompat.from(context).notify(id.hashCode(), builder.build())
    }
}
