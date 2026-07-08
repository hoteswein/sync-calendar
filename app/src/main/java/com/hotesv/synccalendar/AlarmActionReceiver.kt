package com.hotesv.synccalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Обрабатывает нажатия на кнопки действий прямо в уведомлении
 *  (без открытия попапа) — "Завершить" и "Отложить на 5 минут".
 *  Кнопка "Отложить на..." из уведомления ведёт не сюда, а сразу
 *  в ReminderAlarmActivity с открытым меню выбора — там уже есть
 *  диалоговый UI, а из BroadcastReceiver его не показать. */
class AlarmActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISMISS = "com.hotesv.synccalendar.action.DISMISS"
        const val ACTION_SNOOZE = "com.hotesv.synccalendar.action.SNOOZE"
        const val EXTRA_MINUTES = "extra_minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val text = intent.getStringExtra("text") ?: ""

        when (intent.action) {
            ACTION_SNOOZE -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 5)
                ReminderActions.snooze(context, id, text, minutes)
            }
            else -> ReminderActions.dismiss(context, id)
        }

        // если попап с этим же напоминанием сейчас открыт поверх экрана — закрыть и его
        context.sendBroadcast(
            Intent(ReminderAlarmActivity.ACTION_CLOSE_POPUP).apply {
                setPackage(context.packageName)
                putExtra("id", id)
            }
        )
    }
}
