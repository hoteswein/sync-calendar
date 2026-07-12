package com.hotesv.synccalendar

import android.app.NotificationManager
import android.content.Context
import android.net.Uri

/** Общая логика для кнопок "Завершить" / "Отложить" — используется и из
 *  уведомления (AlarmActionReceiver), и из полноэкранного попапа
 *  (ReminderAlarmActivity).
 *
 *  ВАЖНО: "отложить" теперь сохраняет snoozedUntil в сам файл
 *  напоминания, а не только ставит будильник в оперативной памяти
 *  AlarmManager — иначе это состояние нигде не видно (в списке, при
 *  переустановке) и конфликтует с периодическим rescheduleAll. */
object ReminderActions {

    fun dismiss(context: Context, id: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id.hashCode())
    }

    fun snooze(context: Context, id: String, text: String, minutes: Int) {
        dismiss(context, id)

        val folderUriString = Prefs.getFolderUri(context)
        if (folderUriString == null) {
            // папки нет — такое не должно случиться (без неё и напоминаний
            // бы не было), но на всякий случай не роняем приложение
            return
        }
        val repo = SyncRepository(context, Uri.parse(folderUriString))
        val reminder = repo.getReminder(id) ?: return // удалили, пока звонил будильник

        val updated = reminder.copy(snoozedUntil = System.currentTimeMillis() + minutes * 60_000L)
        repo.saveReminder(updated)
        AlarmScheduler.schedule(context, updated)
    }
}
