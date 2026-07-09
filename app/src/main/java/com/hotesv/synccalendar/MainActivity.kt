package com.hotesv.synccalendar

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ReminderAdapter
    private var repo: SyncRepository? = null
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    private val folderPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) onFolderChosen(uri) }

    private val notificationPermissionRequest = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* если откажут — просто не будет уведомлений, приложение не падает */ }

    private val soundPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val previousChannelId = NotificationChannelHelper.channelIdFor(this)
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            Prefs.setSoundUri(this, uri?.toString())
            NotificationChannelHelper.onSoundChanged(this, previousChannelId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = ReminderAdapter(onDelete = ::deleteReminder, onEdit = { showReminderDialog(it) })
        findViewById<RecyclerView>(R.id.remindersRecycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        findViewById<android.view.View>(R.id.addButton).setOnClickListener { showReminderDialog() }
        findViewById<ImageButton>(R.id.soundButton).setOnClickListener { openSoundPicker() }

        requestNotificationPermissionIfNeeded()
        ensureDeviceNameThenFolder()
    }

    // ---------- звук напоминания ----------

    private fun openSoundPicker() {
        val current = Prefs.getSoundUri(this)?.let { Uri.parse(it) }
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION)
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
        }
        soundPickerLauncher.launch(intent)
    }

    /** Постоянная (не одноразовая) строка статуса — так её можно проверить
     *  в любой момент, а не только увидеть один раз при первом запуске.
     *  Обновляется в onResume, в том числе после возврата из настроек. */
    private fun updateFsiStatusRow() {
        val statusView = findViewById<TextView>(R.id.fsiStatusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val allowed = manager.canUseFullScreenIntent()
            statusView.visibility = android.view.View.VISIBLE
            if (allowed) {
                statusView.text = getString(R.string.fsi_status_ok)
                statusView.setTextColor(ContextCompat.getColor(this, R.color.teal))
                statusView.setOnClickListener(null)
                statusView.isClickable = false
            } else {
                statusView.text = getString(R.string.fsi_status_bad)
                statusView.setTextColor(ContextCompat.getColor(this, R.color.danger))
                statusView.isClickable = true
                statusView.setOnClickListener {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    try { startActivity(settingsIntent) } catch (e: Exception) { }
                }
            }
        } else {
            statusView.visibility = android.view.View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        updateFsiStatusRow()
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
        AlertDialog.Builder(this)
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
        repo = SyncRepository(this, uri)
        findViewById<TextView>(R.id.folderPathText).text = uri.path ?: uri.toString()

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
        AlarmScheduler.cancel(this, reminder)
        refreshNow()
    }

    // ---------- добавление / редактирование напоминания ----------

    private fun showReminderDialog(existing: Reminder? = null) {
        val r = repo ?: return
        val view = layoutInflater.inflate(R.layout.dialog_add_reminder, null)
        val textInput = view.findViewById<EditText>(R.id.textInput)
        val dateButton = view.findViewById<android.widget.Button>(R.id.dateButton)
        val timeButton = view.findViewById<android.widget.Button>(R.id.timeButton)
        val devicesContainer = view.findViewById<android.widget.LinearLayout>(R.id.devicesContainer)

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

        AlertDialog.Builder(this)
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
                        myId = myId
                    )
                } else {
                    // отменяем старый будильник перед пересохранением — время могло поменяться
                    AlarmScheduler.cancel(this, existing)
                    existing.copy(
                        text = text,
                        date = chosenDate.format(dateFmt),
                        time = chosenTime.format(timeFmt),
                        targetDeviceIds = targets
                    )
                }
                r.saveReminder(reminder)
                if (targets.contains(myId)) AlarmScheduler.schedule(this, reminder)
                refreshNow()
            }
            .show()
    }
}
