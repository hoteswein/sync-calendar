package com.hotesv.synccalendar

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Логика кнопок попапа/уведомления. Каждое действие есть в двух видах:
 *  "у меня" — трогает только это устройство, никогда не пишет в общий
 *  файл напоминания (локальный оверрайд в Prefs — тот же принцип, что
 *  раньше был багом, теперь осознанно и по назначению);
 *  "у всех" — пишет в сам Reminder, который синхронизируется Syncthing
 *  и разбирается остальными устройствами при их собственном обновлении. */
object ReminderActions {

    private fun cancelLocalNotification(context: Context, id: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id.hashCode())
    }

    private fun closePopupEverywhereOnThisDevice(context: Context, id: String) {
        context.sendBroadcast(
            Intent(ReminderAlarmActivity.ACTION_CLOSE_POPUP).apply {
                setPackage(context.packageName)
                putExtra("id", id)
            }
        )
    }

    private fun repoOrNull(context: Context): SyncRepository? {
        val folderUriString = Prefs.getFolderUri(context) ?: return null
        return SyncRepository(context, Uri.parse(folderUriString))
    }

    /** Завершить только на этом устройстве — просто гасим уведомление/попап,
     *  общий файл не трогаем. */
    fun dismissMine(context: Context, id: String) {
        cancelLocalNotification(context, id)
        closePopupEverywhereOnThisDevice(context, id)
        Prefs.setLocalSnooze(context, id, null)
        DebugLog.add(context, "dismissMine: ${id.take(6)}")
    }

    /** Завершить у всех — гасим у себя сразу же, плюс пишем dismissed_at
     *  в общий файл: остальные устройства погасят своё активное
     *  уведомление при следующем собственном обновлении. */
    fun dismissEveryone(context: Context, id: String) {
        dismissMine(context, id)
        val repo = repoOrNull(context) ?: return
        val reminder = repo.getReminder(id) ?: return
        repo.saveReminder(reminder.copy(dismissedAt = System.currentTimeMillis()))
        DebugLog.add(context, "dismissEveryone: ${id.take(6)}")
    }

    /** Отложить только на этом устройстве — локальный оверрайд, общий
     *  файл напоминания не меняется вообще. */
    fun snoozeMine(context: Context, id: String, text: String, minutes: Int) {
        cancelLocalNotification(context, id)
        closePopupEverywhereOnThisDevice(context, id)
        val until = System.currentTimeMillis() + minutes * 60_000L
        Prefs.setLocalSnooze(context, id, until)
        val repo = repoOrNull(context)
        val reminder = repo?.getReminder(id) ?: Reminder.create(text, "", "", emptyList(), "", id)
        AlarmScheduler.schedule(context, reminder)
        DebugLog.add(context, "snoozeMine: ${id.take(6)} на $minutes мин")
    }

    /** Отложить у всех — пишем snoozed_until в общий файл, синхронизируется
     *  на все устройства и учитывается их собственным планированием. */
    fun snoozeEveryone(context: Context, id: String, text: String, minutes: Int) {
        cancelLocalNotification(context, id)
        closePopupEverywhereOnThisDevice(context, id)
        Prefs.setLocalSnooze(context, id, null) // локальный оверрайд больше не должен перебивать общий

        val repo = repoOrNull(context) ?: return
        val reminder = repo.getReminder(id) ?: return
        val updated = reminder.copy(snoozedUntil = System.currentTimeMillis() + minutes * 60_000L)
        repo.saveReminder(updated)
        AlarmScheduler.schedule(context, updated)
        DebugLog.add(context, "snoozeEveryone: ${id.take(6)} на $minutes мин")
    }
}
