package com.zodiacguard.app.ui.weekly

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.zodiacguard.app.data.ContractDataStore
import com.zodiacguard.app.data.WeeklyStatsHelper
import com.zodiacguard.app.data.WorkDataStore
import com.zodiacguard.app.databinding.FragmentWeeklyBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

class WeeklyFragment : Fragment() {

    private var _binding: FragmentWeeklyBinding? = null
    private val binding get() = _binding!!
    private lateinit var workDataStore: WorkDataStore
    private lateinit var contractDataStore: ContractDataStore
    
    private var calendar = Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWeeklyBinding.inflate(inflater, container, false)
        workDataStore = WorkDataStore(requireContext())
        contractDataStore = ContractDataStore(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnPrevWeek.setOnClickListener {
            calendar.add(Calendar.WEEK_OF_YEAR, -1)
            loadStats()
        }

        binding.btnNextWeek.setOnClickListener {
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
            loadStats()
        }

        binding.btnShareReport.setOnClickListener { shareReport() }

        loadStats()
    }

    private fun loadStats() {
        val year = calendar.get(Calendar.YEAR)
        val week = calendar.get(Calendar.WEEK_OF_YEAR)
        
        binding.tvWeekLabel.text = "Settimana $week - $year"

        viewLifecycleOwner.lifecycleScope.launch {
            val allEntries = workDataStore.entriesFlow.first()
            val weeklyHours = contractDataStore.weeklyHoursFlow.first()
            val workDays = contractDataStore.workDaysFlow.first()
            
            val productionDates = allEntries
                .filter { it.activity == "Produzione" }
                .map { it.date }
                .toSet()
            
            val stats = WeeklyStatsHelper.calculateWeeklyStats(
                allEntries, productionDates, year, week, weeklyHours, workDays
            )
            displayStats(stats)
        }
    }

    private fun displayStats(stats: WeeklyStatsHelper.WeeklyStats) {
        binding.tvTotalWorked.text = "Totale: ${formatMin(stats.totalWorkedMinutes)}"
        binding.tvTotalOrdinary.text = "Ordinarie: ${formatMin(stats.totalOrdinaryMinutes)}"
        binding.tvTotalOvertime.text = "Straordinari: ${formatMin(stats.totalOvertimeMinutes)}"
        binding.tvTotalBank.text = "Banca ore: ${formatMin(stats.totalBankMinutes)}"
        binding.tvTotalTravel.text = "Viaggio: ${formatMin(stats.totalTravelMinutes)}"
        
        val sign = if (stats.balanceMinutes >= 0) "+" else ""
        binding.tvBalance.text = "Saldo: $sign${formatMin(stats.balanceMinutes)}"

        binding.pieChart.setData(listOf(
            Pair(stats.totalOrdinaryMinutes.toFloat(), Color.parseColor("#FFD700")),
            Pair(stats.totalOvertimeMinutes.toFloat(), Color.parseColor("#C41E3A")),
            Pair(stats.totalBankMinutes.toFloat(), Color.parseColor("#4A90E2")),
            Pair(stats.totalTravelMinutes.toFloat(), Color.parseColor("#9C27B0"))
        ))

        binding.tvLegendOrdinary.text = "🟡 Ordinarie: ${formatMin(stats.totalOrdinaryMinutes)}"
        binding.tvLegendOvertime.text = " Straordinari: ${formatMin(stats.totalOvertimeMinutes)}"
        binding.tvLegendBank.text = "🔵 Banca Ore: ${formatMin(stats.totalBankMinutes)}"
        binding.tvLegendTravel.text = "🟣 Viaggio: ${formatMin(stats.totalTravelMinutes)}"

        binding.tvTeatriniCount.text = if (stats.teatriniCount.isEmpty()) "Nessun teatrino" 
            else stats.teatriniCount.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        
        binding.tvLocationsCount.text = if (stats.locationsCount.isEmpty()) "Nessuna presenza" 
            else stats.locationsCount.entries.joinToString("\n") { "${it.key}: ${it.value} gg" }

        binding.tvOutOfTown.text = "🚗 Fuori sede: ${stats.outOfTownDays} gg"
        binding.tvMealTickets.text = "🍽️ Buoni pasto: ${stats.mealTickets}"
    }

    private fun shareReport() {
        val text = """
            📈 RIEPILOGO SETTIMANALE - ${binding.tvWeekLabel.text}
            Totale: ${binding.tvTotalWorked.text.toString().substringAfter(": ")}
            Straordinari: ${binding.tvTotalOvertime.text.toString().substringAfter(": ")}
            Banca ore: ${binding.tvTotalBank.text.toString().substringAfter(": ")}
            Viaggio: ${binding.tvTotalTravel.text.toString().substringAfter(": ")}
            Saldo: ${binding.tvBalance.text.toString().substringAfter(": ")}
            🚗 Fuori sede: ${binding.tvOutOfTown.text.toString().substringAfter(": ")}
            🍽️ Buoni pasto: ${binding.tvMealTickets.text.toString().substringAfter(": ")}
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Condividi"))
    }

    private fun formatMin(min: Int): String {
        return "${min / 60}h ${min % 60}m"
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
