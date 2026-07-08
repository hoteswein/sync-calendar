package com.hotesv.synccalendar

import android.app.NotificationManager
import android.content.Context

/** Общая логика для кнопок "Завершить" / "Отложить" — используется и из
 *  уведомления (AlarmActionReceiver), и из полноэкранного попапа
 *  (ReminderAlarmActivity), чтобы не дублировать код в двух местах. */
object ReminderActions {

    fun dismiss(context: Context, id: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id.hashCode())
    }

    fun snooze(context: Context, id: String, text: String, minutes: Int) {
        dismiss(context, id)
        AlarmScheduler.scheduleSnooze(context, id, text, minutes)
    }
}
