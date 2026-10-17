package com.zodiacguard.app.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zodiacguard.app.R
import com.zodiacguard.app.data.WorkEntry
import java.text.SimpleDateFormat
import java.util.*

class EntryAdapter(
    private var entries: List<WorkEntry>,
    private val onEditClick: (WorkEntry) -> Unit,
    private val onDeleteClick: (WorkEntry) -> Unit
) : RecyclerView.Adapter<EntryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInfo: TextView = view.findViewById(R.id.tvEntryInfo)
        val btnEdit: Button = view.findViewById(R.id.btnEdit)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        holder.tvInfo.text = "${sdf.format(Date(entry.timestamp))} - ${entry.type} | ${entry.activity} | ${entry.location}"

        holder.btnEdit.setOnClickListener { onEditClick(entry) }
        holder.btnDelete.setOnClickListener { onDeleteClick(entry) }
    }

    override fun getItemCount() = entries.size

    fun updateData(newEntries: List<WorkEntry>) {
        entries = newEntries.sortedBy { it.timestamp }
        notifyDataSetChanged()
    }
}
