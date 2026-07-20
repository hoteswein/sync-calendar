package com.hotesv.synccalendar

import android.content.Context
import android.net.Uri
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Лёгкий журнал на телефоне — чтобы видеть, что РЕАЛЬНО происходит
 *  (ставится ли будильник, с каким временем, доходит ли вызов до
 *  AlarmReceiver), не полагаясь на догадки.
 *
 *  Опционально (Prefs.isFileLoggingEnabled) тот же журнал зеркалируется
 *  в подпапку logs/ общей папки синхронизации, файлом с именем этого
 *  устройства — так с любого устройства видно, что происходило у
 *  остальных. */
object DebugLog {
    private const val PREFS_NAME = "sync_calendar_debug_log"
    private const val KEY = "log"
    private const val MAX_LINES = 50
    private val fmt = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")

    fun add(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, "") ?: ""
        val time = LocalDateTime.now().format(fmt)
        val line = "[$time] $message"
        val lines = (listOf(line) + existing.split("\n").filter { it.isNotBlank() }).take(MAX_LINES)
        val combined = lines.joinToString("\n")
        prefs.edit().putString(KEY, combined).apply()

        if (Prefs.isFileLoggingEnabled(context)) {
            mirrorToSyncFolder(context, combined)
        }
    }

    private fun mirrorToSyncFolder(context: Context, content: String) {
        try {
            val folderUriString = Prefs.getFolderUri(context) ?: return
            val deviceName = Prefs.getDeviceName(context)
            val safeName = deviceName.replace(Regex("[^A-Za-zА-Яа-яЁё0-9 _-]"), "_").ifBlank { "device" }
            val repo = SyncRepository(context, Uri.parse(folderUriString))
            repo.writeLogFile(safeName, content)
        } catch (e: Exception) {
            // журналирование не должно ронять приложение, даже если синк недоступен
        }
    }

    fun read(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val text = prefs.getString(KEY, "")
        return if (text.isNullOrBlank()) "(пока пусто)" else text
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
