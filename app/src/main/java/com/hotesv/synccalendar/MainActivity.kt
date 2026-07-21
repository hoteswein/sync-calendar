package com.hotesv.synccalendar

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ReminderAdapter
    private var repo: SyncRepository? = null
    private var currentFolderUri: String? = null
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    private val folderPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) onFolderChosen(uri) }

    private val notificationPermissionRequest = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* если откажут — просто не будет уведомлений, приложение не падает */ }

    private var onReminderSoundPicked: ((Uri) -> Unit)? = null
    private val reminderSoundPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) onReminderSoundPicked?.invoke(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = ReminderAdapter(
            onDelete = ::deleteReminder,
            onEdit = { showReminderDialog(it) },
            onToggleEnabled = ::toggleEnabled,
            onCancelSnooze = ::cancelSnooze
        )
        findViewById<RecyclerView>(R.id.remindersRecycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        findViewById<android.view.View>(R.id.addButton).setOnClickListener { showReminderDialog() }
        findViewById<android.view.View>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        requestNotificationPermissionIfNeeded()
        ensureDeviceNameThenFolder()
    }

    override fun onResume() {
        super.onResume()
        // если папку сменили в настройках — подхватываем здесь
        val savedFolderUri = Prefs.getFolderUri(this)
        if (savedFolderUri != null && savedFolderUri != currentFolderUri) {
            onFolderChosen(Uri.parse(savedFolderUri), alreadyPersisted = true)
        }
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        refreshRunnable?.let { refreshHandler.removeCallbacks(it) }
    }

    // ---------- первоначальная настройка ----------

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun ensureDeviceNameThenFolder() {
        if (Prefs.hasDeviceName(this)) {
            findViewById<TextView>(R.id.deviceNameText).text = Prefs.getDeviceName(this)
            proceedToFolder()
            return
        }
        val input = EditText(this).apply { setText(Build.MODEL ?: "Android") }
        MaterialAlertDialogBuilder(this)
            .setTitle("Имя этого устройства")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().ifBlank { Build.MODEL ?: "Android" }
                Prefs.setDeviceName(this, name)
                findViewById<TextView>(R.id.deviceNameText).text = name
                proceedToFolder()
            }
            .show()
    }

    private fun proceedToFolder() {
        val saved = Prefs.getFolderUri(this)
        if (saved != null) {
            onFolderChosen(Uri.parse(saved), alreadyPersisted = true)
        } else {
            Toast.makeText(this, "Выбери папку, которую синхронизирует Syncthing", Toast.LENGTH_LONG).show()
            folderPicker.launch(null)
        }
    }

    private fun onFolderChosen(uri: Uri, alreadyPersisted: Boolean = false) {
        if (!alreadyPersisted) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            Prefs.setFolderUri(this, uri.toString())
        }
        currentFolderUri = uri.toString()
        repo = SyncRepository(this, uri)

        val myId = Prefs.getOrCreateDeviceId(this)
        val myName = Prefs.getDeviceName(this)
        repo?.saveDevice(DeviceInfo.mine(myId, myName))

        refreshNow()
    }

    // ---------- список / обновление ----------

    private fun startAutoRefresh() {
        val runnable = object : Runnable {
            override fun run() {
                refreshNow()
                refreshHandler.postDelayed(this, 10_000)
            }
        }
        refreshRunnable = runnable
        refreshHandler.postDelayed(runnable, 10_000)
    }

    private fun refreshNow() {
        val r = repo ?: return
        val devices = r.listDevices()
        val reminders = r.listReminders()

        adapter.submit(reminders, devices)
        findViewById<android.view.View>(R.id.emptyText).visibility =
            if (reminders.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        val myId = Prefs.getOrCreateDeviceId(this)
        AlarmScheduler.rescheduleAll(this, myId, reminders)
    }

    private fun deleteReminder(reminder: Reminder) {
        repo?.deleteReminder(reminder.id)
        repo?.deleteReminderSound(reminder.id)
        AlarmScheduler.cancel(this, reminder)
        refreshNow()
    }

    private fun toggleEnabled(reminder: Reminder, enabled: Boolean) {
        val updated = reminder.copy(enabled = enabled)
        repo?.saveReminder(updated)
        AlarmScheduler.schedule(this, updated)
        refreshNow()
    }

    private fun cancelSnooze(reminder: Reminder) {
        val updated = reminder.copy(snoozedUntil = null)
        repo?.saveReminder(updated)
        AlarmScheduler.schedule(this, updated)
        refreshNow()
    }

    // ---------- добавление / редактирование напоминания ----------

    private fun showReminderDialog(existing: Reminder? = null) {
        val r = repo ?: return
        val reminderId = existing?.id ?: java.util.UUID.randomUUID().toString()
        val view = layoutInflater.inflate(R.layout.dialog_add_reminder, null)
        val textInput = view.findViewById<EditText>(R.id.textInput)
        val dateButton = view.findViewById<android.widget.Button>(R.id.dateButton)
        val timeButton = view.findViewById<android.widget.Button>(R.id.timeButton)
        val devicesContainer = view.findViewById<android.widget.LinearLayout>(R.id.devicesContainer)
        val soundButton = view.findViewById<android.widget.Button>(R.id.reminderSoundButton)

        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

        var chosenDate = existing?.date?.let { runCatching { LocalDate.parse(it, dateFmt) }.getOrNull() }
            ?: LocalDate.now()
        var chosenTime = existing?.time?.let { runCatching { LocalTime.parse(it, timeFmt) }.getOrNull() }
            ?: LocalTime.of(9, 0)

        if (existing != null) textInput.setText(existing.text)

        fun refreshButtons() {
            dateButton.text = chosenDate.format(dateFmt)
            timeButton.text = chosenTime.format(timeFmt)
        }
        refreshButtons()

        dateButton.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                chosenDate = LocalDate.of(y, m + 1, d)
                refreshButtons()
            }, chosenDate.year, chosenDate.monthValue - 1, chosenDate.dayOfMonth).show()
        }
        timeButton.setOnClickListener {
            TimePickerDialog(this, { _, h, min ->
                chosenTime = LocalTime.of(h, min)
                refreshButtons()
            }, chosenTime.hour, chosenTime.minute, true).show()
        }

        // ---------- мелодия для этого напоминания ----------
        val hasExistingSound = r.reminderSoundFile(reminderId) != null
        var pendingSoundUri: Uri? = null
        var soundCleared = false

        fun refreshSoundButton() {
            soundButton.text = when {
                pendingSoundUri != null -> getString(R.string.reminder_sound_pending)
                soundCleared -> getString(R.string.reminder_sound_default)
                hasExistingSound -> getString(R.string.reminder_sound_custom)
                else -> getString(R.string.reminder_sound_default)
            }
        }
        refreshSoundButton()

        soundButton.setOnClickListener {
            val hasSoundNow = (pendingSoundUri != null) || (hasExistingSound && !soundCleared)
            val options = if (hasSoundNow) {
                arrayOf(getString(R.string.reminder_sound_choose), getString(R.string.reminder_sound_reset))
            } else {
                arrayOf(getString(R.string.reminder_sound_choose))
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reminder_sound_dialog_title)
                .setItems(options) { _, which ->
                    if (which == 0) {
                        onReminderSoundPicked = { uri ->
                            pendingSoundUri = uri
                            soundCleared = false
                            refreshSoundButton()
                        }
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        }
                        reminderSoundPickerLauncher.launch(intent)
                    } else {
                        pendingSoundUri = null
                        soundCleared = true
                        refreshSoundButton()
                    }
                }
                .show()
        }

        val myId = Prefs.getOrCreateDeviceId(this)
        val devices = r.listDevices()
        val checkboxes = mutableListOf<Pair<CheckBox, String>>()
        devices.forEach { d ->
            val cb = CheckBox(this).apply {
                text = d.name + if (d.id == myId) " " + getString(R.string.this_device_suffix) else ""
                isChecked = existing?.targetDeviceIds?.contains(d.id) ?: (d.id == myId)
            }
            devicesContainer.addView(cb)
            checkboxes.add(cb to d.id)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.new_reminder_title else R.string.edit_reminder_title)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val text = textInput.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                val targets = checkboxes.filter { it.first.isChecked }.map { it.second }
                    .ifEmpty { listOf(myId) }

                val reminder = if (existing == null) {
                    Reminder.create(
                        text = text,
                        date = chosenDate.format(dateFmt),
                        time = chosenTime.format(timeFmt),
                        targets = targets,
                        myId = myId,
                        id = reminderId
                    )
                } else {
                    AlarmScheduler.cancel(this, existing)
                    existing.copy(
                        text = text,
                        date = chosenDate.format(dateFmt),
                        time = chosenTime.format(timeFmt),
                        targetDeviceIds = targets
                    )
                }
                r.saveReminder(reminder)

                val pickedUri = pendingSoundUri
                if (pickedUri != null) {
                    val ext = guessAudioExtension(pickedUri)
                    r.saveReminderSound(reminderId, pickedUri, ext)
                } else if (soundCleared) {
                    r.deleteReminderSound(reminderId)
                }

                if (targets.contains(myId)) AlarmScheduler.schedule(this, reminder)
                refreshNow()
            }
            .show()
    }

    private fun guessAudioExtension(uri: Uri): String {
        return when (contentResolver.getType(uri)) {
            "audio/mpeg" -> "mp3"
            "audio/ogg" -> "ogg"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/mp4", "audio/x-m4a" -> "m4a"
            "audio/flac" -> "flac"
            else -> "audio"
        }
    }
}
