package com.zodiacguard.app.ui.monthly

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
        binding.btnBackup.setOnClickListener { createBackup() }

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
            val productionDates = workDataStore.productionDaysFlow.first()
            
            val stats = MonthlyStatsHelper.calculateMonthlyStats(
                allEntries, productionDates, currentYear, currentMonth, weeklyHours, workDays
            )

            displayStats(stats, weeklyHours, workDays)
        }
    }

    private fun displayStats(stats: MonthlyStatsHelper.MonthlyStats, weeklyHours: Int, workDays: Int) {
        binding.tvTotalWorked.text = "Totale lavorate: ${MonthlyStatsHelper.minutesToText(stats.totalWorkedMinutes)}"
        binding.tvContractExpected.text = "Contratto attese (${stats.workingDaysInMonth} gg): ${MonthlyStatsHelper.minutesToText(stats.totalContractMinutes)}"
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

        val dailyContractMinutes = (weeklyHours * 60) / workDays
        val expectedMinutes = dailyContractMinutes * stats.workingDaysInMonth
        
        binding.pieChart.setData(
            listOf(
                Pair(stats.totalOrdinaryMinutes.toFloat(), Color.parseColor("#FFD700")),
                Pair(stats.totalOvertimeMinutes.toFloat(), Color.parseColor("#C41E3A")),
                Pair(stats.totalBankMinutes.toFloat(), Color.parseColor("#4A90E2")),
                Pair(stats.totalTravelMinutes.toFloat(), Color.parseColor("#9C27B0"))
            ),
            expectedMinutes.toFloat()
        )

        binding.tvLegendOrdinary.text = "🟡 Ordinarie: ${MonthlyStatsHelper.minutesToText(stats.totalOrdinaryMinutes)}"
        binding.tvLegendOvertime.text = "🔴 Straordinari: ${MonthlyStatsHelper.minutesToText(stats.totalOvertimeMinutes)}"
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

        val agendaAdapter = DailyAgendaAdapter(stats.dailyStats) { day ->
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
            val productionDates = workDataStore.productionDaysFlow.first()
            
            val stats = MonthlyStatsHelper.calculateMonthlyStats(
                allEntries, productionDates, currentYear, currentMonth, weeklyHours, workDays
            )

            val report = MonthlyStatsHelper.generateDetailedReport(stats, binding.tvMonthYear.text.toString())
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Riepilogo ${binding.tvMonthYear.text}")
                putExtra(Intent.EXTRA_TEXT, report)
            }
            
            startActivity(Intent.createChooser(intent, "Condividi riepilogo"))
        }
    }

    private fun createBackup() {
        viewLifecycleOwner.lifecycleScope.launch {
            val allEntries = workDataStore.entriesFlow.first()
            val weeklyHours = contractDataStore.weeklyHoursFlow.first()
            val workDays = contractDataStore.workDaysFlow.first()
            val productionDates = workDataStore.productionDaysFlow.first()
            
            val notes = emptyMap<String, String>()
            
            val backup = MonthlyStatsHelper.exportAllData(
                allEntries, productionDates, notes, weeklyHours, workDays
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Backup ZodiacGuard ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}")
                putExtra(Intent.EXTRA_TEXT, backup)
            }
            
            startActivity(Intent.createChooser(intent, "Salva Backup"))
            Toast.makeText(requireContext(), "Backup generato!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
