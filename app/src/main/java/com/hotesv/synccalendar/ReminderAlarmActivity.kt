package com.hotesv.synccalendar

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Показывается поверх блокировки экрана и всех приложений, когда
 *  срабатывает напоминание — как обычный будильник. Крутит звук по
 *  кругу, пока не нажата одна из кнопок; кнопка "назад" намеренно
 *  ничего не делает, чтобы попап нельзя было закрыть просто свайпом. */
class ReminderAlarmActivity : AppCompatActivity() {

    companion object {
        const val ACTION_CLOSE_POPUP = "com.hotesv.synccalendar.action.CLOSE_POPUP"

        // те же варианты, что в задаче: минуты/часы + ручной ввод отдельно
        val SNOOZE_OPTIONS = listOf(
            1 to "1 минута",
            3 to "3 минуты",
            5 to "5 минут",
            10 to "10 минут",
            30 to "30 минут",
            60 to "1 час",
            180 to "3 часа",
            360 to "6 часов"
        )
    }

    private var mediaPlayer: MediaPlayer? = null
    private var reminderId: String = ""
    private var reminderText: String = ""

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getStringExtra("id") == reminderId) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLockScreenFlags()
        setContentView(R.layout.activity_reminder_alarm)

        reminderId = intent.getStringExtra("id") ?: ""
        reminderText = intent.getStringExtra("text") ?: getString(R.string.app_name)

        findViewById<TextView>(R.id.textReminder).text = reminderText

        findViewById<Button>(R.id.buttonDismissMine).setOnClickListener {
            ReminderActions.dismissMine(this, reminderId)
            finish()
        }
        findViewById<Button>(R.id.buttonDismissEveryone).setOnClickListener {
            ReminderActions.dismissEveryone(this, reminderId)
            finish()
        }
        findViewById<Button>(R.id.buttonSnooze5Mine).setOnClickListener {
            ReminderActions.snoozeMine(this, reminderId, reminderText, 5)
            finish()
        }
        findViewById<Button>(R.id.buttonSnooze5Everyone).setOnClickListener {
            ReminderActions.snoozeEveryone(this, reminderId, reminderText, 5)
            finish()
        }
        findViewById<Button>(R.id.buttonSnoozeMenuMine).setOnClickListener { showSnoozeMenu(forEveryone = false) }
        findViewById<Button>(R.id.buttonSnoozeMenuEveryone).setOnClickListener { showSnoozeMenu(forEveryone = true) }

        // попап закрывается только по одной из кнопок, не свайпом "назад"
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* специально ничего не делаем */ }
        })

        val filter = IntentFilter(ACTION_CLOSE_POPUP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeReceiver, filter)
        }

        startAlarmSound()

        if (intent.getBooleanExtra("open_snooze_menu", false)) {
            showSnoozeMenu(forEveryone = true)
        }
    }

    private fun applyLockScreenFlags() {
        // minSdk уже 30, так что эти API (26-27) доступны безусловно
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        km.requestDismissKeyguard(this, null)
    }

    private fun showSnoozeMenu(forEveryone: Boolean) {
        val labels = (SNOOZE_OPTIONS.map { it.second } + getString(R.string.snooze_custom)).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.snooze_dialog_title)
            .setItems(labels) { _, which ->
                if (which < SNOOZE_OPTIONS.size) {
                    val minutes = SNOOZE_OPTIONS[which].first
                    if (forEveryone) {
                        ReminderActions.snoozeEveryone(this, reminderId, reminderText, minutes)
                    } else {
                        ReminderActions.snoozeMine(this, reminderId, reminderText, minutes)
                    }
                    finish()
                } else {
                    showCustomSnoozeDialog(forEveryone)
                }
            }
            .show()
    }

    private fun showCustomSnoozeDialog(forEveryone: Boolean) {
        val picker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 999
            value = 10
            wrapSelectorWheel = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.snooze_custom)
            .setView(customSnoozeDialogView(picker))
            .setPositiveButton(R.string.snooze_confirm) { _, _ ->
                if (forEveryone) {
                    ReminderActions.snoozeEveryone(this, reminderId, reminderText, picker.value)
                } else {
                    ReminderActions.snoozeMine(this, reminderId, reminderText, picker.value)
                }
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** NumberPicker вместо EditText: попап живёт поверх lock-screen окна
     *  (setShowWhenLocked), а системная клавиатура там периодически валит
     *  процесс при фокусе на поле ввода. FOCUS_BLOCK_DESCENDANTS запрещает
     *  внутреннему полю пикера получать фокус — клавиатура не вызывается
     *  вообще, даже тапом по числу. */
    private fun customSnoozeDialogView(picker: NumberPicker): LinearLayout {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * density).toInt(), (8 * density).toInt(), (24 * density).toInt(), 0)
            addView(TextView(context).apply { text = context.getString(R.string.snooze_custom_hint) })
            addView(picker)
        }
    }

    private fun startAlarmSound() {
        val uri: Uri = intent.getStringExtra("sound_uri")?.let { Uri.parse(it) }
            ?: Prefs.getSoundUri(this)?.let { Uri.parse(it) }
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@ReminderAlarmActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // не получилось проиграть звук — попап всё равно показан, не критично
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (e: Exception) { }
            it.release()
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        stopAlarmSound()
        try { unregisterReceiver(closeReceiver) } catch (e: Exception) { }
        super.onDestroy()
    }
}
