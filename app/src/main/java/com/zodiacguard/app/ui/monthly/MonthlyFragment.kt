package com.zodiacguard.app.ui.monthly

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.zodiacguard.app.R
import com.zodiacguard.app.data.ContractDataStore
import com.zodiacguard.app.data.MonthlyStatsHelper
import com.zodiacguard.app.data.WorkDataStore
import com.zodiacguard.app.databinding.FragmentMonthlyBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MonthlyFragment : Fragment() {

    private var _binding: FragmentMonthlyBinding? = null
    private val binding get() = _binding!!
    private lateinit var workDataStore: WorkDataStore
    private lateinit var contractDataStore: ContractDataStore
    
    private var currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMonthlyBinding.inflate(inflater, container, false)
        workDataStore = WorkDataStore(requireContext())
        contractDataStore = ContractDataStore(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateMonthDisplay()

        binding.btnPrevMonth.setOnClickListener {
            currentMonth--
            if (currentMonth < 1) { currentMonth = 12; currentYear-- }
            updateMonthDisplay()
            loadStats()
        }

        binding.btnNextMonth.setOnClickListener {
            currentMonth++
            if (currentMonth > 12) { currentMonth = 1; currentYear++ }
            updateMonthDisplay()
            loadStats()
        }

        binding.btnShareReport.setOnClickListener { shareReport() }

        loadStats()
    }

    private fun updateMonthDisplay() {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.set(currentYear, currentMonth - 1, 1)
        binding.tvMonthYear.text = sdf.format(cal.time).replaceFirstChar { it.uppercase() }
    }

    private fun loadStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            val allEntries = workDataStore.entriesFlow.first()
            val weeklyHours = contractDataStore.weeklyHoursFlow.first()
            val workDays = contractDataStore.workDaysFlow.first()
            
            val productionDates = allEntries
                .filter { it.activity == "Produzione" }
                .map { it.date }
                .toSet()
            
            val stats = MonthlyStatsHelper.calculateMonthlyStats(
                allEntries, productionDates, currentYear, currentMonth, weeklyHours, workDays
            )

            displayStats(stats)
        }
    }

    private fun displayStats(stats: MonthlyStatsHelper.MonthlyStats) {
        binding.tvTotalWorked.text = "Totale lavorate: ${MonthlyStatsHelper.minutesToText(stats.totalWorkedMinutes)}"
        binding.tvContractExpected.text = "Contratto attese (${stats.workingDaysInMonth} gg lavorativi): ${MonthlyStatsHelper.minutesToText(stats.totalContractMinutes)}"
        binding.tvTotalOrdinary.text = "Ordinarie: ${MonthlyStatsHelper.minutesToText(stats.totalOrdinaryMinutes)}"
        binding.tvTotalOvertime.text = "Straordinari: ${MonthlyStatsHelper.minutesToText(stats.totalOvertimeMinutes)}"
        binding.tvTotalBank.text = "Banca ore: ${MonthlyStatsHelper.minutesToText(stats.totalBankMinutes)}"
        binding.tvTotalTravel.text = "Viaggio: ${MonthlyStatsHelper.minutesToText(stats.totalTravelMinutes)}"
        
        val balanceText = if (stats.balanceMinutes >= 0) {
            "Saldo: +${MonthlyStatsHelper.minutesToText(stats.balanceMinutes)}"
        } else {
            "Saldo: -${MonthlyStatsHelper.minutesToText(-stats.balanceMinutes)}"
        }
        binding.tvBalance.text = balanceText

        binding.pieChart.setData(listOf(
            Pair(stats.totalOrdinaryMinutes.toFloat(), Color.parseColor("#FFD700")),
            Pair(stats.totalOvertimeMinutes.toFloat(), Color.parseColor("#C41E3A")),
            Pair(stats.totalBankMinutes.toFloat(), Color.parseColor("#4A90E2")),
            Pair(stats.totalTravelMinutes.toFloat(), Color.parseColor("#9C27B0"))
        ))

        binding.tvLegendOrdinary.text = "🟡 Ordinarie: ${MonthlyStatsHelper.minutesToText(stats.totalOrdinaryMinutes)}"
        binding.tvLegendOvertime.text = " Straordinari: ${MonthlyStatsHelper.minutesToText(stats.totalOvertimeMinutes)}"
        binding.tvLegendBank.text = "🔵 Banca Ore: ${MonthlyStatsHelper.minutesToText(stats.totalBankMinutes)}"
        binding.tvLegendTravel.text = "🟣 Viaggio: ${MonthlyStatsHelper.minutesToText(stats.totalTravelMinutes)}"

        if (stats.teatriniDays.isEmpty()) {
            binding.tvTeatriniDays.text = "Nessun teatrino"
        } else {
            binding.tvTeatriniDays.text = stats.teatriniDays.joinToString(", ") { MonthlyStatsHelper.formatDate(it) }
        }

        if (stats.teatriniCount.isEmpty()) {
            binding.tvTeatriniCount.text = "Nessun teatrino"
        } else {
            binding.tvTeatriniCount.text = stats.teatriniCount.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        }

        if (stats.locationsCount.isEmpty()) {
            binding.tvLocationsCount.text = "Nessuna presenza"
        } else {
            binding.tvLocationsCount.text = stats.locationsCount.entries.joinToString("\n") { "${it.key}: ${it.value} giorni" }
        }

        binding.tvOutOfTown.text = "🚗 Giornate fuori sede: ${stats.outOfTownDays}"
        binding.tvMealTickets.text = "🍽️ Buoni pasto: ${stats.mealTickets}"

        // Agenda cliccabile
        val agendaAdapter = DailyAgendaAdapter(stats.dailyStats) { day ->
            // Naviga al dettaglio del giorno
            val bundle = Bundle()
            bundle.putString("date", day.date)
            findNavController().navigate(R.id.dayDetailFragment, bundle)
        }
        binding.recyclerViewAgenda.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewAgenda.adapter = agendaAdapter
    }

    private fun shareReport() {
        viewLifecycleOwner.lifecycleScope.launch {
            val allEntries = workDataStore.entriesFlow.first()
            val weeklyHours = contractDataStore.weeklyHoursFlow.first()
            val workDays = contractDataStore.workDaysFlow.first()
            
            val productionDates = allEntries
                .filter { it.activity == "Produzione" }
                .map { it.date }
                .toSet()
            
            val stats = MonthlyStatsHelper.calculateMonthlyStats(
                allEntries, productionDates, currentYear, currentMonth, weeklyHours, workDays
            )

            val report = buildReport(stats)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Riepilogo ${binding.tvMonthYear.text}")
                putExtra(Intent.EXTRA_TEXT, report)
            }
            
            startActivity(Intent.createChooser(intent, "Condividi riepilogo"))
        }
    }

    private fun buildReport(stats: MonthlyStatsHelper.MonthlyStats): String {
        val sb = StringBuilder()
        sb.appendLine("📊 RIEPILOGO MENSILE - ${binding.tvMonthYear.text}")
        sb.appendLine("=" .repeat(40))
        sb.appendLine()
        sb.appendLine("⏰ ORE LAVORATE")
        sb.appendLine("Totale: ${MonthlyStatsHelper.minutesToText(stats.totalWorkedMinutes)}")
        sb.appendLine("Contratto attese (${stats.workingDaysInMonth} gg): ${MonthlyStatsHelper.minutesToText(stats.totalContractMinutes)}")
        sb.appendLine("Ordinarie: ${MonthlyStatsHelper.minutesToText(stats.totalOrdinaryMinutes)}")
        sb.appendLine("Straordinari: ${MonthlyStatsHelper.minutesToText(stats.totalOvertimeMinutes)}")
        sb.appendLine("Banca ore: ${MonthlyStatsHelper.minutesToText(stats.totalBankMinutes)}")
        sb.appendLine("Viaggio: ${MonthlyStatsHelper.minutesToText(stats.totalTravelMinutes)}")
        sb.appendLine()
        sb.appendLine(" SALDO: ${if (stats.balanceMinutes >= 0) "+" else ""}${MonthlyStatsHelper.minutesToText(stats.balanceMinutes)}")
        sb.appendLine()
        sb.appendLine("🎭 GIORNI CON TEATRINI")
        if (stats.teatriniDays.isEmpty()) {
            sb.appendLine("Nessun teatrino")
        } else {
            stats.teatriniDays.forEach { sb.appendLine("- ${MonthlyStatsHelper.formatDate(it)}") }
        }
        sb.appendLine()
        sb.appendLine("🎭 TEATRINI PER TIPO")
        if (stats.teatriniCount.isEmpty()) {
            sb.appendLine("Nessun teatrino")
        } else {
            stats.teatriniCount.forEach { (type, count) -> sb.appendLine("$type: $count") }
        }
        sb.appendLine()
        sb.appendLine("📍 PRESENZE PER SEDE")
        stats.locationsCount.forEach { (location, days) -> sb.appendLine("$location: $days giorni") }
        sb.appendLine()
        sb.appendLine("🚗 Giornate fuori sede: ${stats.outOfTownDays}")
        sb.appendLine("️ Buoni pasto: ${stats.mealTickets}")
        
        return sb.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
