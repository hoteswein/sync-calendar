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

    /** Единая точка планирования. Учитывает и enabled, и snoozedUntil —
     *  поэтому и обычное (пере)расписание, и отложенный будильник идут
     *  через одну и ту же функцию и не могут разъехаться друг с другом
     *  (раньше "отложить" жило только в оперативной памяти AlarmManager
     *  и ничего не знало про периодическое rescheduleAll). */
    fun schedule(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (!reminder.enabled) {
            alarmManager.cancel(pendingIntent(context, reminder))
            return
        }

        val now = System.currentTimeMillis()
        val trigger = if (reminder.snoozedUntil != null && reminder.snoozedUntil > now) {
            reminder.snoozedUntil
        } else {
            triggerAtMillis(reminder) ?: return
        }
        if (trigger <= now) return

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent(context, reminder))
    }

    fun cancel(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, reminder))
    }

    /** Пересобрать все будильники с нуля — вызывается при старте
     *  приложения, при автообновлении и после перезагрузки устройства. */
    fun rescheduleAll(context: Context, myDeviceId: String, reminders: List<Reminder>) {
        reminders
            .filter { it.targetDeviceIds.contains(myDeviceId) }
            .forEach { schedule(context, it) }
    }
}
