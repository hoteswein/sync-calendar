package com.hotesv.synccalendar

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject

/** Работает поверх дерева, выбранного пользователем через
 *  ACTION_OPEN_DOCUMENT_TREE (это та самая папка, которую
 *  синхронизирует Syncthing). Больше никаких путей файловой системы
 *  напрямую — начиная с Android 10+ это и не сработало бы. */
class SyncRepository(private val context: Context, private val treeUri: Uri) {

    private fun root(): DocumentFile? = DocumentFile.fromTreeUri(context, treeUri)

    private fun dir(name: String): DocumentFile? {
        val r = root() ?: return null
        return r.findFile(name) ?: r.createDirectory(name)
    }

    private fun readText(doc: DocumentFile): String? = try {
        context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes().decodeToString() }
    } catch (e: Exception) {
        null // повреждённый/недописанный файл — пропускаем, не валим весь список
    }

    private fun writeText(dirDoc: DocumentFile, filename: String, content: String) {
        val existing = dirDoc.findFile(filename)
        val target = existing ?: dirDoc.createFile("application/json", filename)
        ?: throw IllegalStateException("Не удалось создать $filename")
        context.contentResolver.openOutputStream(target.uri, "wt")?.use {
            it.write(content.toByteArray())
        }
    }

    fun listDevices(): List<DeviceInfo> {
        val d = dir("devices") ?: return emptyList()
        return d.listFiles()
            .filter { it.name?.endsWith(".json") == true }
            .mapNotNull { f -> readText(f)?.let { runCatching { DeviceInfo.fromJson(JSONObject(it)) }.getOrNull() } }
    }

    fun listReminders(): List<Reminder> {
        val d = dir("reminders") ?: return emptyList()
        return d.listFiles()
            .filter { it.name?.endsWith(".json") == true }
            .mapNotNull { f -> readText(f)?.let { runCatching { Reminder.fromJson(JSONObject(it)) }.getOrNull() } }
    }

    fun getReminder(id: String): Reminder? {
        val d = dir("reminders") ?: return null
        val f = d.findFile("$id.json") ?: return null
        return readText(f)?.let { runCatching { Reminder.fromJson(JSONObject(it)) }.getOrNull() }
    }

    fun saveDevice(device: DeviceInfo) {
        val d = dir("devices") ?: return
        writeText(d, "${device.id}.json", device.toJson().toString())
    }

    fun saveReminder(reminder: Reminder) {
        val d = dir("reminders") ?: return
        writeText(d, "${reminder.id}.json", reminder.toJson().toString())
    }

    fun deleteReminder(id: String) {
        val d = dir("reminders") ?: return
        d.findFile("$id.json")?.delete()
    }
}
