package com.hotesv.synccalendar

import android.content.Context
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Общая логика меню "Отложить на..." — и попап-активность (заблокированный
 *  экран), и overlay-сервис (поверх активных приложений) показывают один и
 *  тот же список вариантов. asOverlay=true — для контекста без своей
 *  Activity (Service), иначе диалог не встанет в правильное окно. */
object SnoozeMenuHelper {

    fun show(
        context: Context,
        reminderId: String,
        reminderText: String,
        forEveryone: Boolean,
        asOverlay: Boolean,
        onDone: () -> Unit
    ) {
        val labels = (ReminderAlarmActivity.SNOOZE_OPTIONS.map { it.second } + context.getString(R.string.snooze_custom))
            .toTypedArray()
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.snooze_dialog_title)
            .setItems(labels) { _, which ->
                if (which < ReminderAlarmActivity.SNOOZE_OPTIONS.size) {
                    val minutes = ReminderAlarmActivity.SNOOZE_OPTIONS[which].first
                    apply(context, reminderId, reminderText, forEveryone, minutes)
                    onDone()
                } else {
                    showCustom(context, reminderId, reminderText, forEveryone, asOverlay, onDone)
                }
            }
            .create()
        if (asOverlay) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
    }

    private fun showCustom(
        context: Context,
        reminderId: String,
        reminderText: String,
        forEveryone: Boolean,
        asOverlay: Boolean,
        onDone: () -> Unit
    ) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = context.getString(R.string.snooze_custom_hint)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.snooze_custom)
            .setView(input)
            .setPositiveButton(R.string.snooze_confirm) { _, _ ->
                val minutes = input.text.toString().toIntOrNull()
                if (minutes != null && minutes > 0) {
                    apply(context, reminderId, reminderText, forEveryone, minutes)
                    onDone()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        if (asOverlay) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
    }

    private fun apply(context: Context, reminderId: String, reminderText: String, forEveryone: Boolean, minutes: Int) {
        if (forEveryone) {
            ReminderActions.snoozeEveryone(context, reminderId, reminderText, minutes)
        } else {
            ReminderActions.snoozeMine(context, reminderId, reminderText, minutes)
        }
    }
}
