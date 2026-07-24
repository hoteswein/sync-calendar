package com.hotesv.synccalendar

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {

    private val folderPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            Prefs.setFolderUri(this, uri.toString())
            updateFolderRow()
        }
    }

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
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.rowFolder).setOnClickListener { folderPicker.launch(null) }
        findViewById<View>(R.id.rowSound).setOnClickListener { openSoundPicker() }
        findViewById<View>(R.id.rowFsi).setOnClickListener { openFsiSettings() }
        findViewById<View>(R.id.rowExactAlarm).setOnClickListener { openExactAlarmSettings() }
        findViewById<View>(R.id.rowBattery).setOnClickListener { openBatterySettings() }
        findViewById<View>(R.id.rowAutostart).setOnClickListener { openAutostart() }
        findViewById<View>(R.id.rowDebugLog).setOnClickListener { showDebugLog() }
        findViewById<View>(R.id.rowOtherLogs).setOnClickListener { showOtherLogsPicker() }

        findViewById<SwitchCompat>(R.id.fileLogSwitch).apply {
            isChecked = Prefs.isFileLoggingEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setFileLoggingEnabled(this@SettingsActivity, checked)
                if (checked) DebugLog.add(this@SettingsActivity, "логирование в файл включено")
            }
        }

        findViewById<SwitchCompat>(R.id.serviceSwitch).apply {
            isChecked = Prefs.isServiceEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setServiceEnabled(this@SettingsActivity, checked)
                val serviceIntent = Intent(this@SettingsActivity, ReliabilityService::class.java)
                if (checked) {
                    ContextCompat.startForegroundService(this@SettingsActivity, serviceIntent)
                } else {
                    stopService(serviceIntent)
                }
            }
        }

        updateFolderRow()
    }

    override fun onResume() {
        super.onResume()
        updateFsiRow()
        updateExactAlarmRow()
        updateBatteryRow()
        updateAutostartRowVisibility()
        // если разрешение дали/забрали в системных настройках — отражаем честно
        val overlaySwitch = findViewById<SwitchCompat>(R.id.overlaySwitch)
        val overlayGranted = android.provider.Settings.canDrawOverlays(this)
        if (!overlayGranted && Prefs.isOverlayModeEnabled(this)) {
            Prefs.setOverlayModeEnabled(this, false)
        }
        overlaySwitch.setOnCheckedChangeListener(null)
        overlaySwitch.isChecked = overlayGranted && Prefs.isOverlayModeEnabled(this)
        overlaySwitch.setOnCheckedChangeListener { switchView, checked ->
            if (checked && !android.provider.Settings.canDrawOverlays(this)) {
                switchView.isChecked = false
                Toast.makeText(this, R.string.overlay_permission_needed, Toast.LENGTH_LONG).show()
                try {
                    startActivity(Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    ))
                } catch (e: Exception) { }
            } else {
                Prefs.setOverlayModeEnabled(this, checked)
            }
        }
    }

    private fun updateFolderRow() {
        val uriString = Prefs.getFolderUri(this)
        findViewById<TextView>(R.id.folderPathText).text =
            uriString?.let { Uri.parse(it).path } ?: getString(R.string.pick_folder)
    }

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

    private fun updateFsiRow() {
        val statusView = findViewById<TextView>(R.id.fsiStatusText)
        val allowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()
        } else {
            true
        }
        if (allowed) {
            statusView.text = getString(R.string.fsi_status_ok)
            statusView.setTextColor(ContextCompat.getColor(this, R.color.teal))
        } else {
            statusView.text = getString(R.string.fsi_status_bad)
            statusView.setTextColor(ContextCompat.getColor(this, R.color.danger))
        }
    }

    private fun openFsiSettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.fromParts("package", packageName, null)
            })
        } catch (e: Exception) { }
    }

    // ---------- точные будильники (самая вероятная причина полной тишины) ----------

    private fun updateExactAlarmRow() {
        val statusView = findViewById<TextView>(R.id.exactAlarmStatusText)
        val allowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else {
            true
        }
        if (allowed) {
            statusView.text = getString(R.string.exact_alarm_status_ok)
            statusView.setTextColor(ContextCompat.getColor(this, R.color.teal))
        } else {
            statusView.text = getString(R.string.exact_alarm_status_bad)
            statusView.setTextColor(ContextCompat.getColor(this, R.color.danger))
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
            } catch (e: Exception) { }
        }
    }

    private fun updateBatteryRow() {
        val statusView = findViewById<TextView>(R.id.batteryStatusText)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            statusView.text = getString(R.string.battery_status_ok)
            statusView.setTextColor(ContextCompat.getColor(this, R.color.teal))
        } else {
            statusView.text = getString(R.string.battery_status_bad)
            statusView.setTextColor(ContextCompat.getColor(this, R.color.danger))
        }
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (e: Exception) { }
    }

    private fun updateAutostartRowVisibility() {
        val isXiaomiFamily = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
            Build.MANUFACTURER.equals("POCO", ignoreCase = true)
        findViewById<View>(R.id.rowAutostart).visibility = if (isXiaomiFamily) View.VISIBLE else View.GONE
    }

    private fun openAutostart() {
        try {
            startActivity(Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            })
        } catch (e: Exception) {
            Toast.makeText(this, R.string.autostart_not_found, Toast.LENGTH_LONG).show()
        }
    }

    // ---------- журнал событий ----------

    private fun showDebugLog() {
        val scrollView = android.widget.ScrollView(this)
        val textView = TextView(this).apply {
            text = DebugLog.read(this@SettingsActivity)
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text))
            setPadding(32, 24, 32, 24)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        scrollView.addView(textView)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_debug_log_label)
            .setView(scrollView)
            .setNegativeButton(R.string.debug_log_clear) { _, _ ->
                DebugLog.clear(this)
                Toast.makeText(this, "Очищено", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton(R.string.debug_log_close, null)
            .show()
    }

    private fun showOtherLogsPicker() {
        val folderUriString = Prefs.getFolderUri(this)
        if (folderUriString == null) {
            Toast.makeText(this, R.string.no_other_logs, Toast.LENGTH_LONG).show()
            return
        }
        val repo = SyncRepository(this, Uri.parse(folderUriString))
        val mySafeName = Prefs.getDeviceName(this)
            .replace(Regex("[^A-Za-zА-Яа-яЁё0-9 _-]"), "_").ifBlank { "device" }
        val files = repo.listLogFiles().filter { it != "$mySafeName.log" }

        if (files.isEmpty()) {
            Toast.makeText(this, R.string.no_other_logs, Toast.LENGTH_LONG).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_other_logs_label)
            .setItems(files.toTypedArray()) { _, which ->
                val content = repo.readLogFile(files[which]) ?: "(не удалось прочитать)"
                showLogContent(files[which], content)
            }
            .show()
    }

    private fun showLogContent(title: String, content: String) {
        val scrollView = android.widget.ScrollView(this)
        val textView = TextView(this).apply {
            text = content
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text))
            setPadding(32, 24, 32, 24)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        scrollView.addView(textView)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton(R.string.debug_log_close, null)
            .show()
    }
}
