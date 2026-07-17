package com.hotesv.synccalendar

import android.content.Context
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Лёгкий журнал на телефоне — чтобы видеть, что РЕАЛЬНО происходит
 *  (ставится ли будильник, с каким временем, доходит ли вызов до
 *  AlarmReceiver), не полагаясь на догадки и системные экраны разрешений,
 *  которые сами по себе не показывают, что происходит внутри кода. */
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
        prefs.edit().putString(KEY, lines.joinToString("\n")).apply()
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
