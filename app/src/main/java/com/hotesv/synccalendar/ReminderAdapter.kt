package com.hotesv.synccalendar

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ReminderAdapter(
    private val onDelete: (Reminder) -> Unit,
    private val onEdit: (Reminder) -> Unit,
    private val onToggleEnabled: (Reminder, Boolean) -> Unit,
    private val onCancelSnooze: (Reminder) -> Unit
) : RecyclerView.Adapter<ReminderAdapter.VH>() {

    private var items: List<Reminder> = emptyList()
    private var devicesById: Map<String, DeviceInfo> = emptyMap()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun submit(reminders: List<Reminder>, devices: List<DeviceInfo>) {
        items = reminders.sortedWith(compareBy({ it.date }, { it.time }))
        devicesById = devices.associateBy { it.id }
        notifyDataSetChanged()
    }

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.timeText)
        val date: TextView = view.findViewById(R.id.dateText)
        val text: TextView = view.findViewById(R.id.textText)
        val devices: TextView = view.findViewById(R.id.devicesText)
        val snoozeStatus: TextView = view.findViewById(R.id.snoozeStatusText)
        val enabledSwitch: SwitchCompat = view.findViewById(R.id.enabledSwitch)
        val delete: ImageButton = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.time.text = r.time
        holder.date.text = r.date
        holder.text.text = r.text
        holder.text.alpha = if (r.enabled) 1f else 0.4f
        holder.devices.text = r.targetDeviceIds.joinToString(" · ") { id ->
            devicesById[id]?.name ?: id.take(6)
        }

        val now = System.currentTimeMillis()
        if (r.snoozedUntil != null && r.snoozedUntil > now) {
            val remainingSec = (r.snoozedUntil - now) / 1000
            val min = remainingSec / 60
            val sec = remainingSec % 60
            val until = Instant.ofEpochMilli(r.snoozedUntil).atZone(ZoneId.systemDefault()).format(timeFmt)
            holder.snoozeStatus.visibility = android.view.View.VISIBLE
            holder.snoozeStatus.text = "⏰ Отложено до $until (ещё ${min} мин ${sec} сек) — нажми, чтобы отменить"
            holder.snoozeStatus.setOnClickListener { onCancelSnooze(r) }
        } else {
            holder.snoozeStatus.visibility = android.view.View.GONE
            holder.snoozeStatus.setOnClickListener(null)
        }

        holder.enabledSwitch.setOnCheckedChangeListener(null)
        holder.enabledSwitch.isChecked = r.enabled
        holder.enabledSwitch.setOnCheckedChangeListener { _, checked -> onToggleEnabled(r, checked) }

        holder.delete.setOnClickListener { onDelete(r) }
        // тап по строке (кроме переключателя/кнопки удаления/статуса снюза) — редактирование
        holder.itemView.setOnClickListener { onEdit(r) }
    }
}
