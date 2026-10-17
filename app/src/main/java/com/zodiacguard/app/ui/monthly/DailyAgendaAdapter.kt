package com.zodiacguard.app.ui.monthly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zodiacguard.app.data.MonthlyStatsHelper
import com.zodiacguard.app.databinding.ItemDailyAgendaBinding

class DailyAgendaAdapter(
    private var dailyStats: List<MonthlyStatsHelper.DailySummary>,
    private val onDayClick: (MonthlyStatsHelper.DailySummary) -> Unit
) : RecyclerView.Adapter<DailyAgendaAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemDailyAgendaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDailyAgendaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val day = dailyStats[position]
        
        holder.binding.tvDate.text = MonthlyStatsHelper.formatDate(day.date)
        holder.binding.tvActivityLocation.text = "${day.activity} - ${day.location}"
        holder.binding.tvHours.text = MonthlyStatsHelper.minutesToText(day.totalMinutes)
        
        if (day.isTeatrino) {
            holder.binding.tvTeatrinoBadge.visibility = View.VISIBLE
        } else {
            holder.binding.tvTeatrinoBadge.visibility = View.GONE
        }
        
        if (day.location != "Modena") {
            holder.binding.tvTravelBadge.visibility = View.VISIBLE
        } else {
            holder.binding.tvTravelBadge.visibility = View.GONE
        }
        
        holder.binding.root.setOnClickListener {
            onDayClick(day)
        }
    }

    override fun getItemCount() = dailyStats.size

    fun updateData(newStats: List<MonthlyStatsHelper.DailySummary>) {
        dailyStats = newStats.sortedBy { it.date }
        notifyDataSetChanged()
    }
}
