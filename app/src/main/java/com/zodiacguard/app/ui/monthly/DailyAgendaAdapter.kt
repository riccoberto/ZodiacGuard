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

    private var showAllDays = false
    private val initialVisibleDays = 7

    class ViewHolder(val binding: ItemDailyAgendaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDailyAgendaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val day = visibleDailyStats()[position]
        
        holder.binding.tvDate.text = MonthlyStatsHelper.formatDate(day.date)
        holder.binding.tvActivityLocation.text = "${day.activity} - ${day.location}"
        holder.binding.tvHours.text = MonthlyStatsHelper.minutesToText(day.totalMinutes)
        
        // Simbolo teatrino
        holder.binding.tvTeatrinoBadge.visibility = if (day.isTeatrino) View.VISIBLE else View.GONE
        
        holder.binding.tvTravelBadge.visibility =
            if (day.travelMinutes > 0) View.VISIBLE else View.GONE
        
        // Simbolo produzione
        holder.binding.tvProductionBadge.visibility = if (day.isProduction) View.VISIBLE else View.GONE
        
        // NUOVO: Simbolo buono pasto
        holder.binding.tvMealBadge.visibility = if (day.hasMealTicket) View.VISIBLE else View.GONE
        
        // Click sulla riga
        holder.binding.root.setOnClickListener {
            onDayClick(day)
        }
    }

    override fun getItemCount() = visibleDailyStats().size

    fun updateData(newStats: List<MonthlyStatsHelper.DailySummary>) {
        dailyStats = newStats.sortedBy { it.date }
        notifyDataSetChanged()
    }

    fun hasMoreThanInitialDays(): Boolean = dailyStats.size > initialVisibleDays

    fun toggleExpanded(): Boolean {
        showAllDays = !showAllDays
        notifyDataSetChanged()
        return showAllDays
    }

    private fun visibleDailyStats(): List<MonthlyStatsHelper.DailySummary> =
        if (showAllDays) dailyStats else dailyStats.take(initialVisibleDays)
}
