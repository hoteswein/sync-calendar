package com.hotesv.synccalendar

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ReminderAdapter(
    private val onDelete: (Reminder) -> Unit
) : RecyclerView.Adapter<ReminderAdapter.VH>() {

    private var items: List<Reminder> = emptyList()
    private var devicesById: Map<String, DeviceInfo> = emptyMap()

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
        holder.devices.text = r.targetDeviceIds.joinToString(" · ") { id ->
            devicesById[id]?.name ?: id.take(6)
        }
        holder.delete.setOnClickListener { onDelete(r) }
    }
}
