package com.hotesv.synccalendar

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

/** Показывает попап поверх ЛЮБОГО активного приложения (включая игры в
 *  полноэкранном режиме) через SYSTEM_ALERT_WINDOW — в отличие от
 *  full-screen intent (ReminderAlarmActivity), который система сама не
 *  всплывает, если экран уже разблокирован и используется. Используется
 *  только когда экран разблокирован и это разрешение включено в Настройках
 *  — при заблокированном экране по-прежнему работает обычный попап. */
class OverlayAlarmService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var mediaPlayer: MediaPlayer? = null
    private var reminderId: String = ""
    private var reminderText: String = ""

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getStringExtra("id") == reminderId) hideOverlay()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || overlayView != null) return START_NOT_STICKY
        reminderId = intent.getStringExtra("id") ?: return START_NOT_STICKY
        reminderText = intent.getStringExtra("text") ?: getString(R.string.app_name)
        val soundUriString = intent.getStringExtra("sound_uri")

        showOverlay()
        startAlarmSound(soundUriString)

        val filter = IntentFilter(ReminderAlarmActivity.ACTION_CLOSE_POPUP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeReceiver, filter)
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = LayoutInflater.from(this).inflate(R.layout.activity_reminder_alarm, null)

        view.findViewById<TextView>(R.id.textReminder).text = reminderText

        view.findViewById<Button>(R.id.buttonDismissMine).setOnClickListener {
            ReminderActions.dismissMine(this, reminderId)
            hideOverlay()
        }
        view.findViewById<Button>(R.id.buttonDismissEveryone).setOnClickListener {
            ReminderActions.dismissEveryone(this, reminderId)
            hideOverlay()
        }
        view.findViewById<Button>(R.id.buttonSnooze5Mine).setOnClickListener {
            ReminderActions.snoozeMine(this, reminderId, reminderText, 5)
            hideOverlay()
        }
        view.findViewById<Button>(R.id.buttonSnooze5Everyone).setOnClickListener {
            ReminderActions.snoozeEveryone(this, reminderId, reminderText, 5)
            hideOverlay()
        }
        view.findViewById<Button>(R.id.buttonSnoozeMenuMine).setOnClickListener {
            SnoozeMenuHelper.show(this, reminderId, reminderText, forEveryone = false, asOverlay = true) { hideOverlay() }
        }
        view.findViewById<Button>(R.id.buttonSnoozeMenuEveryone).setOnClickListener {
            SnoozeMenuHelper.show(this, reminderId, reminderText, forEveryone = true, asOverlay = true) { hideOverlay() }
        }

        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        try {
            windowManager?.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            DebugLog.add(this, "OverlayAlarmService: не удалось показать overlay — ${e.message}")
            stopSelf()
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) { }
        }
        overlayView = null
        stopAlarmSound()
        try { unregisterReceiver(closeReceiver) } catch (e: Exception) { }
        stopSelf()
    }

    private fun startAlarmSound(soundUriString: String?) {
        val uri: Uri = soundUriString?.let { Uri.parse(it) }
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
                setDataSource(this@OverlayAlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // не получилось проиграть звук — попап всё равно показан
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
        hideOverlay()
        super.onDestroy()
    }
}
