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
}
