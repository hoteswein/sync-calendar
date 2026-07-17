package com.hotesv.synccalendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AlarmScheduler {

    private val logFmt = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")

    private fun fmt(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(logFmt)

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
     *  через одну и ту же функцию и не могут разъехаться друг с другом.
     *  Логирует КАЖДЫЙ исход, включая "тихие" ранние выходы — именно они
     *  чаще всего и есть источник необъяснимой тишины. */
    fun schedule(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val tag = "\"${reminder.text.take(20)}\" (${reminder.id.take(6)})"

        if (!reminder.enabled) {
            alarmManager.cancel(pendingIntent(context, reminder))
            DebugLog.add(context, "schedule: $tag — enabled=false, снято")
            return
        }

        val now = System.currentTimeMillis()
        val trigger = if (reminder.snoozedUntil != null && reminder.snoozedUntil > now) {
            reminder.snoozedUntil
        } else {
            val t = triggerAtMillis(reminder)
            if (t == null) {
                DebugLog.add(context, "schedule: $tag — НЕ УДАЛОСЬ распарсить date/time='${reminder.date} ${reminder.time}'")
                return
            }
            t
        }
        if (trigger <= now) {
            DebugLog.add(context, "schedule: $tag — trigger=${fmt(trigger)} уже в прошлом (now=${fmt(now)}), пропущен")
            return
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent(context, reminder))
        val exact = if (android.os.Build.VERSION.SDK_INT >= 31) {
            try { alarmManager.canScheduleExactAlarms() } catch (e: Exception) { true }
        } else {
            true
        }
        DebugLog.add(context, "schedule: $tag — ПОСТАВЛЕН на ${fmt(trigger)} (сейчас ${fmt(now)}), canScheduleExactAlarms=$exact")
    }

    fun cancel(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, reminder))
        DebugLog.add(context, "cancel: \"${reminder.text.take(20)}\" (${reminder.id.take(6)})")
    }

    /** Пересобрать все будильники с нуля — вызывается при старте
     *  приложения, при автообновлении и после перезагрузки устройства. */
    fun rescheduleAll(context: Context, myDeviceId: String, reminders: List<Reminder>) {
        reminders
            .filter { it.targetDeviceIds.contains(myDeviceId) }
            .forEach { schedule(context, it) }
    }
}
