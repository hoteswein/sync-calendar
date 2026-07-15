package com.hotesv.synccalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val folderUriString = Prefs.getFolderUri(context) ?: return
        val myId = Prefs.getOrCreateDeviceId(context)
        val repo = SyncRepository(context, Uri.parse(folderUriString))

        val reminders = repo.listReminders()
        AlarmScheduler.rescheduleAll(context, myId, reminders)

        if (Prefs.isServiceEnabled(context)) {
            ContextCompat.startForegroundService(context, Intent(context, ReliabilityService::class.java))
        }
    }
}
