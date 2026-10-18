package com.zodiacguard.app.ui.monthly

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.zodiacguard.app.R
import com.zodiacguard.app.data.MonthlyStatsHelper
import com.zodiacguard.app.data.WorkDataStore
import com.zodiacguard.app.data.WorkEntry
import com.zodiacguard.app.databinding.FragmentDayDetailBinding
import com.zodiacguard.app.ui.dashboard.EntryAdapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DayDetailFragment : Fragment() {

    private var _binding: FragmentDayDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var workDataStore: WorkDataStore
    private var selectedDate: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDayDetailBinding.inflate(inflater, container, false)
        workDataStore = WorkDataStore(requireContext())
        
        selectedDate = arguments?.getString("date") ?: WorkEntry.getCurrentDate()
        
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sdfFull = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)
        binding.tvDayDate.text = if (date != null) sdfFull.format(date) else selectedDate

        binding.btnBackToMonth.setOnClickListener {
            findNavController().popBackStack()
        }

        loadDayDetails()
    }

    private fun loadDayDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            val allEntries = workDataStore.entriesFlow.first()
            val dayEntries = allEntries.filter { it.date == selectedDate }.sortedBy { it.timestamp }
            
            if (dayEntries.isEmpty()) {
                binding.tvDayActivity.text = "Nessuna marcatura"
                binding.tvDayLocation.text = ""
                binding.tvDayTotal.text = "Totale: 0h 00m"
                return@launch
            }

            val activity = dayEntries.first().activity
            val location = dayEntries.first().location
            val productionDates = workDataStore.productionDaysFlow.first()
            val isProduction = productionDates.contains(selectedDate)

            var workMinutes = 0
            var lastEntryTime: Long? = null
            for (entry in dayEntries) {
                if (entry.type == "ENTRATA") {
                    lastEntryTime = entry.timestamp
                } else if (entry.type == "USCITA" && lastEntryTime != null) {
                    workMinutes += ((entry.timestamp - lastEntryTime) / 60000).toInt()
                    lastEntryTime = null
                }
            }

            val travelMinutes = when (location) {
                "Vignola", "Castelfranco" -> 90
                "Bologna" -> 120
                else -> 0
            }

            val totalMinutes = workMinutes + travelMinutes
            
            var mealTickets = 0
            var lastExitTime: Long? = null
            for (entry in dayEntries) {
                if (entry.type == "USCITA") {
                    lastExitTime = entry.timestamp
                } else if (entry.type == "ENTRATA" && lastExitTime != null) {
                    val pauseMinutes = ((entry.timestamp - lastExitTime) / 60000).toInt()
                    if (pauseMinutes < 120 && pauseMinutes > 0) {
                        mealTickets += 2
                    }
                    lastExitTime = null
                }
            }

            binding.tvDayActivity.text = "Attività: $activity"
            binding.tvDayLocation.text = "Sede: $location ${if (location != "Modena") "" else ""}"
            binding.tvDayTotal.text = "Totale ore: ${MonthlyStatsHelper.minutesToText(totalMinutes)}"
            binding.tvDayTravel.text = "Viaggio: ${MonthlyStatsHelper.minutesToText(travelMinutes)}"
            binding.tvDayProduction.text = "Modalità Produzione: ${if (isProduction) "SÌ" else "NO"}"
            binding.tvDayMealTicket.text = "🍽️ Buoni pasto: $mealTickets"

            val adapter = EntryAdapter(
                dayEntries,
                onEditClick = { /* modifica da qui */ },
                onDeleteClick = { entry ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        workDataStore.deleteEntry(entry)
                        loadDayDetails()
                    }
                }
            )
            binding.recyclerViewDayEntries.layoutManager = LinearLayoutManager(requireContext())
            binding.recyclerViewDayEntries.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
