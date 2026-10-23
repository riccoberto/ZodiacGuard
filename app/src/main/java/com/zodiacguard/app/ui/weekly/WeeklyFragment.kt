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
import java.util.Locale

class WeeklyFragment : Fragment() {

    private var _binding: FragmentWeeklyBinding? = null
    private val binding get() = _binding!!
    private lateinit var workDataStore: WorkDataStore
    private lateinit var contractDataStore: ContractDataStore
    
    private var calendar = Calendar.getInstance(Locale.ITALY).apply {
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
    }

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
        val year = calendar.getWeekYear()
        val week = calendar.get(Calendar.WEEK_OF_YEAR)
        
        viewLifecycleOwner.lifecycleScope.launch {
            val allEntries = workDataStore.entriesFlow.first()
            val weeklyHours = contractDataStore.weeklyHoursFlow.first()
            val weeklyMinutes = contractDataStore.weeklyMinutesFlow.first()
            
            val productionDates = workDataStore.productionDaysFlow.first()
            
            val stats = WeeklyStatsHelper.calculateWeeklyStats(
                allEntries, productionDates, year, week, weeklyHours, weeklyMinutes
            )
            displayStats(stats)
        }
    }

    private fun displayStats(stats: WeeklyStatsHelper.WeeklyStats) {
        binding.tvWeekLabel.text = "Settimana ${calendar.get(Calendar.WEEK_OF_YEAR)} - ${stats.weekStartDate} / ${stats.weekEndDate}"
        
        binding.tvTotalWorked.text = "Totale lavorate + viaggio: ${formatMin(stats.totalWorkedMinutes)}"
        binding.tvContractExpected.text =
            "Contratto settimanale: ${formatMin(stats.totalContractMinutes)}"
        binding.tvTotalOrdinary.text = "Ordinarie: ${formatMin(stats.totalOrdinaryMinutes)}"
        binding.tvTotalOvertime.text = "Straordinari: ${formatMin(stats.totalOvertimeMinutes)}"
        binding.tvTotalBank.text = "Banca ore: ${formatMin(stats.totalBankMinutes)}"
        binding.tvTotalTravel.text = "Viaggio: ${formatMin(stats.totalTravelMinutes)}"
        
        val sign = if (stats.balanceMinutes >= 0) "+" else ""
        binding.tvBalance.text = "Saldo: $sign${formatMin(stats.balanceMinutes)}"

        val expectedMinutes = stats.totalContractMinutes
        
        // NUOVI COLORI
        binding.pieChart.setData(
            listOf(
                Pair(stats.totalOrdinaryMinutes.toFloat(), Color.parseColor("#FFD700")),
                Pair(stats.totalOvertimeMinutes.toFloat(), Color.parseColor("#FF5252")),
                Pair(stats.totalBankMinutes.toFloat(), Color.parseColor("#64B5F6"))
            ),
            expectedMinutes.toFloat(),
            stats.totalWorkedMinutes
        )

        binding.tvLegendOrdinary.text = "🟡 Ordinarie: ${formatMin(stats.totalOrdinaryMinutes)}"
        binding.tvLegendOvertime.text = "🔴 Straordinari: ${formatMin(stats.totalOvertimeMinutes)}"
        binding.tvLegendBank.text = "🔵 Banca Ore: ${formatMin(stats.totalBankMinutes)}"
        binding.tvLegendTravel.text =
            "🟣 Viaggio (compreso nel totale): ${formatMin(stats.totalTravelMinutes)}"

        binding.tvTeatriniCount.text = if (stats.teatriniCount.isEmpty()) "Nessun teatrino" 
            else stats.teatriniCount.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        
        binding.tvLocationsCount.text = if (stats.locationsCount.isEmpty()) "Nessuna presenza" 
            else stats.locationsCount.entries.joinToString("\n") { "${it.key}: ${it.value} gg" }

        binding.tvOutOfTown.text = "🚗 Fuori sede: ${stats.outOfTownDays} gg"
        binding.tvMealTickets.text = "🍽️ Buoni pasto: ${stats.mealTickets}"
    }

    private fun shareReport() {
        val summary = binding.tvTeatriniCount.text.toString()
        val locations = binding.tvLocationsCount.text.toString()
        val text = """
            📈 RIEPILOGO SETTIMANALE - ${binding.tvWeekLabel.text.toString()}
            Totale: ${binding.tvTotalWorked.text.toString().substringAfter(": ")}
            Contratto settimanale: ${binding.tvContractExpected.text.toString().substringAfter(": ")}
            Ordinarie: ${binding.tvTotalOrdinary.text.toString().substringAfter(": ")}
            Straordinari: ${binding.tvTotalOvertime.text.toString().substringAfter(": ")}
            Banca ore: ${binding.tvTotalBank.text.toString().substringAfter(": ")}
            Viaggio: ${binding.tvTotalTravel.text.toString().substringAfter(": ")}
            Saldo: ${binding.tvBalance.text.toString().substringAfter(": ")}
            Teatrini: ${statsSummary(summary)}
            Sedi nella settimana: ${statsSummary(locations)}
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

    private fun statsSummary(value: String): String =
        value.ifBlank { "Nessuno" }.replace("\n", ", ")

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
