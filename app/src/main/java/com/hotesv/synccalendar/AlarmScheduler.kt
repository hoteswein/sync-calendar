package com.hotesv.synccalendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object AlarmScheduler {

    private fun pendingIntent(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("id", reminder.id)
            putExtra("text", reminder.text)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun triggerAtMillis(reminder: Reminder): Long? = try {
        val date = LocalDate.parse(reminder.date)
        val time = LocalTime.parse(reminder.time)
        LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null // некорректная дата/время в файле — просто не планируем алярм
    }

    fun schedule(context: Context, reminder: Reminder) {
        val trigger = triggerAtMillis(reminder) ?: return
        if (trigger <= System.currentTimeMillis()) return // уже прошло — не планируем

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            trigger,
            pendingIntent(context, reminder)
        )
    }

    fun cancel(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, reminder))
    }

    /** Будильник "отложить" — через minutes минут от текущего момента,
     *  под тем же id/text, что у исходного напоминания. Используется
     *  кнопками "Отложить на 5 минут" / "Отложить на..." */
    fun scheduleSnooze(context: Context, id: String, text: String, minutes: Int) {
        val trigger = System.currentTimeMillis() + minutes * 60_000L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("text", text)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    }

    /** Пересобрать все будильники с нуля — вызывается при старте
     *  приложения и после перезагрузки устройства: сами алярмы
     *  reboot не переживают, а файлы могли прилететь по Syncthing,
     *  пока телефон был выключен. */
    fun rescheduleAll(context: Context, myDeviceId: String, reminders: List<Reminder>) {
        reminders
            .filter { it.targetDeviceIds.contains(myDeviceId) }
            .forEach { schedule(context, it) }
    }
}
