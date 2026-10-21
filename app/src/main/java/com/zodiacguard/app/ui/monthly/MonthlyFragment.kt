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
import com.zodiacguard.app.data.PdfReportGenerator
import com.zodiacguard.app.data.UserProfilePreferences
import com.zodiacguard.app.data.WorkDataStore
import com.zodiacguard.app.databinding.FragmentMonthlyBinding
import kotlinx.coroutines.Job
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
    private var currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1 // 1-12
    private var currentStats: MonthlyStatsHelper.MonthlyStats? = null
    private var statsLoadJob: Job? = null

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

        // Collega il pulsante al nuovo generatore PDF
        binding.btnShareReport.setOnClickListener { shareReportAsPdf() }

        loadStats()
    }

    private fun updateMonthDisplay() {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.set(currentYear, currentMonth - 1, 1)
        binding.tvMonthYear.text = sdf.format(cal.time).replaceFirstChar { it.uppercase() }
    }

    private fun loadStats() {
        val year = currentYear
        val month = currentMonth
        currentStats = null
        binding.btnShareReport.isEnabled = false
        statsLoadJob?.cancel()
        statsLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val allEntries = workDataStore.entriesFlow.first()
            val weeklyHours = contractDataStore.weeklyHoursFlow.first()
            val workDays = contractDataStore.workDaysFlow.first()
            val productionDates = workDataStore.productionDaysFlow.first()
            
            val stats = MonthlyStatsHelper.calculateMonthlyStats(
                allEntries, productionDates, year, month, weeklyHours, workDays
            )

            if (year != currentYear || month != currentMonth) return@launch
            currentStats = stats
            displayStats(stats, weeklyHours, workDays)
            binding.btnShareReport.isEnabled = true
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
                Pair(stats.totalOvertimeMinutes.toFloat(), Color.parseColor("#FF5252")),
                Pair(stats.totalBankMinutes.toFloat(), Color.parseColor("#64B5F6")),
                Pair(stats.totalTravelMinutes.toFloat(), Color.parseColor("#B388FF"))
            ),
            expectedMinutes.toFloat()
        )

        binding.tvOutOfTown.text = "🚗 Giornate fuori sede: ${stats.outOfTownDays}"
        binding.tvMealTickets.text = "🍽️ Buoni pasto: ${stats.mealTickets}"

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

        val agendaAdapter = DailyAgendaAdapter(stats.dailyStats) { day ->
            val bundle = Bundle()
            bundle.putString("date", day.date)
            findNavController().navigate(R.id.dayDetailFragment, bundle)
        }
        binding.recyclerViewAgenda.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewAgenda.adapter = agendaAdapter
    }

    // NUOVA FUNZIONE: Genera e condivide PDF
    private fun shareReportAsPdf() {
        val stats = currentStats
        if (stats == null) {
            Toast.makeText(requireContext(), "Riepilogo mensile non ancora disponibile", Toast.LENGTH_SHORT).show()
            return
        }
        val year = currentYear
        val month = currentMonth
        val monthLabel = binding.tvMonthYear.text.toString()

        // Disabilita il pulsante per evitare doppi click
        binding.btnShareReport.isEnabled = false
        binding.btnShareReport.text = "⏳ Generazione PDF in corso..."
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uri = PdfReportGenerator(requireContext()).generateMonthlyPdf(
                    year,
                    month,
                    stats,
                    UserProfilePreferences.getDisplayName(requireContext())
                )
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_SUBJECT, "Riepilogo $monthLabel")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(Intent.createChooser(intent, "Condividi PDF Riepilogo"))
                Toast.makeText(requireContext(), "PDF generato e pronto per la condivisione", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Errore generazione PDF: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                // Ripristina il pulsante
                binding.btnShareReport.isEnabled = true
                binding.btnShareReport.text = "📄 ESPORTA RIEPILOGO PDF"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
