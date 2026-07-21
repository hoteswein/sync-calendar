package com.hotesv.synccalendar

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Ровно тот же протокол, что мы обкатали в Python-прототипе:
 *  devices/<uuid>.json и reminders/<uuid>.json, без общих файлов,
 *  в которые пишут сразу несколько устройств. */

data class DeviceInfo(
    val id: String,
    val name: String,
    val platform: String,
    val lastSeen: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("platform", platform)
        put("last_seen", lastSeen)
    }

    companion object {
        fun fromJson(o: JSONObject): DeviceInfo = DeviceInfo(
            id = o.getString("id"),
            name = o.optString("name", "unnamed"),
            platform = o.optString("platform", "Android"),
            lastSeen = o.optString("last_seen", "")
        )

        fun mine(id: String, name: String): DeviceInfo =
            DeviceInfo(id, name, "Android", Instant.now().toString())
    }
}

data class Reminder(
    val id: String,
    val text: String,
    val date: String,       // "yyyy-MM-dd"
    val time: String,       // "HH:mm"
    val targetDeviceIds: List<String>,
    val createdBy: String,
    val createdAt: String,
    val enabled: Boolean = true,        // выключенное напоминание хранится, но не звонит
    val snoozedUntil: Long? = null      // epoch millis; null = не отложено сейчас
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("date", date)
        put("time", time)
        put("target_device_ids", JSONArray(targetDeviceIds))
        put("created_by", createdBy)
        put("created_at", createdAt)
        put("enabled", enabled)
        if (snoozedUntil != null) put("snoozed_until", snoozedUntil) else remove("snoozed_until")
    }

    companion object {
        fun fromJson(o: JSONObject): Reminder {
            val ids = mutableListOf<String>()
            val arr = o.optJSONArray("target_device_ids")
            if (arr != null) {
                for (i in 0 until arr.length()) ids.add(arr.getString(i))
            }
            return Reminder(
                id = o.getString("id"),
                text = o.optString("text", ""),
                date = o.optString("date", ""),
                time = o.optString("time", "09:00"),
                targetDeviceIds = ids,
                createdBy = o.optString("created_by", ""),
                createdAt = o.optString("created_at", ""),
                // старые файлы без этих полей — enabled по умолчанию true, не отложено
                enabled = if (o.has("enabled")) o.optBoolean("enabled", true) else true,
                snoozedUntil = if (o.has("snoozed_until")) o.optLong("snoozed_until") else null
            )
        }

        fun create(
            text: String,
            date: String,
            time: String,
            targets: List<String>,
            myId: String,
            id: String = UUID.randomUUID().toString()
        ): Reminder =
            Reminder(
                id = id,
                text = text,
                date = date,
                time = time,
                targetDeviceIds = targets,
                createdBy = myId,
                createdAt = Instant.now().toString()
            )
    }
}
