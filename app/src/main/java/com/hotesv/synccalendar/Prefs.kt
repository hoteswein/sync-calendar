package com.hotesv.synccalendar

import android.content.Context
import java.util.UUID

/** Идентичность устройства хранится локально (переживает перезапуск
 *  приложения, но не переустановку — это installation ID, а не
 *  hardware ID, ровно как договаривались: см. обсуждение про
 *  hardware ID на Android). */
object Prefs {
    private const val FILE = "sync_calendar_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_FOLDER_URI = "folder_uri"
    private const val KEY_SOUND_URI = "sound_uri"
    private const val KEY_SERVICE_ENABLED = "reliability_service_enabled"
    private const val KEY_FILE_LOGGING = "file_logging_enabled"
    private const val KEY_OVERLAY_MODE = "overlay_mode_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getOrCreateDeviceId(context: Context): String {
        val p = prefs(context)
        val existing = p.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val fresh = UUID.randomUUID().toString()
        p.edit().putString(KEY_DEVICE_ID, fresh).apply()
        return fresh
    }

    fun hasDeviceName(context: Context): Boolean =
        prefs(context).getString(KEY_DEVICE_NAME, null) != null

    fun getDeviceName(context: Context): String =
        prefs(context).getString(KEY_DEVICE_NAME, null) ?: android.os.Build.MODEL ?: "Android"

    fun setDeviceName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    fun getFolderUri(context: Context): String? =
        prefs(context).getString(KEY_FOLDER_URI, null)

    fun setFolderUri(context: Context, uri: String) {
        prefs(context).edit().putString(KEY_FOLDER_URI, uri).apply()
    }

    /** null означает "звук по умолчанию" (системный звук уведомлений) */
    fun getSoundUri(context: Context): String? =
        prefs(context).getString(KEY_SOUND_URI, null)

    fun setSoundUri(context: Context, uri: String?) {
        prefs(context).edit().putString(KEY_SOUND_URI, uri).apply()
    }

    fun isServiceEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SERVICE_ENABLED, false)

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun isFileLoggingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FILE_LOGGING, false)

    fun setFileLoggingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FILE_LOGGING, enabled).apply()
    }

    // ---------- локальный снюз "у меня" — намеренно НЕ синхронизируется ----------

    fun getLocalSnooze(context: Context, reminderId: String): Long? {
        val v = prefs(context).getLong("local_snooze_$reminderId", -1L)
        return if (v > 0) v else null
    }

    fun setLocalSnooze(context: Context, reminderId: String, untilMillis: Long?) {
        val editor = prefs(context).edit()
        if (untilMillis == null) editor.remove("local_snooze_$reminderId")
        else editor.putLong("local_snooze_$reminderId", untilMillis)
        editor.apply()
    }

    fun isOverlayModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY_MODE, false)

    fun setOverlayModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_OVERLAY_MODE, enabled).apply()
    }
}
