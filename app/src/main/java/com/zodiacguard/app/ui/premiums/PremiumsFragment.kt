package com.zodiacguard.app.ui.premiums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.zodiacguard.app.data.PremiumDaysHelper
import com.zodiacguard.app.data.WorkDataStore
import com.zodiacguard.app.databinding.FragmentPremiumsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PremiumsFragment : Fragment() {

    private var _binding: FragmentPremiumsBinding? = null
    private val binding get() = _binding!!
    private lateinit var workDataStore: WorkDataStore
    private var currentYear = Calendar.getInstance().get(Calendar.YEAR)
    private var currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPremiumsBinding.inflate(inflater, container, false)
        workDataStore = WorkDataStore(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnPrevMonth.setOnClickListener {
            currentMonth--
            if (currentMonth < 1) {
                currentMonth = 12
                currentYear--
            }
            loadSummary()
        }
        binding.btnNextMonth.setOnClickListener {
            currentMonth++
            if (currentMonth > 12) {
                currentMonth = 1
                currentYear++
            }
            loadSummary()
        }
        loadSummary()
    }

    private fun loadSummary() {
        val year = currentYear
        val month = currentMonth
        binding.tvMonthYear.text = SimpleDateFormat("MMMM yyyy", Locale.ITALIAN)
            .format(Calendar.getInstance().apply { set(year, month - 1, 1) }.time)
            .replaceFirstChar { it.uppercase(Locale.ITALIAN) }

        viewLifecycleOwner.lifecycleScope.launch {
            val entries = workDataStore.entriesFlow.first()
            if (year != currentYear || month != currentMonth) return@launch
            displaySummary(PremiumDaysHelper.calculateMonthlySummary(entries, year, month))
        }
    }

    private fun displaySummary(summary: PremiumDaysHelper.MonthlySummary) {
        binding.tvHolidayHours.text = "Ore registrate in festività: ${formatMinutes(summary.holidayMinutes)}"
        binding.tvSundayHours.text = "Ore registrate di domenica: ${formatMinutes(summary.sundayMinutes)}"
        binding.tvOverlapHours.text =
            "Ore in domenica festiva (già incluse nei totali): ${formatMinutes(summary.holidayAndSundayMinutes)}"

        binding.layoutSpecialDays.removeAllViews()
        if (summary.specialDays.isEmpty()) {
            addDetailRow("Nessuna domenica o festività nel mese.")
            return
        }

        summary.specialDays.forEach { day ->
            val title = PremiumDaysHelper.formatDate(day.date)
            val labels = day.labels.joinToString(" · ")
            addDetailRow("$title\n$labels\nOre da marcature: ${formatMinutes(day.workedMinutes)}")
        }
    }

    private fun addDetailRow(text: String) {
        val row = android.widget.TextView(requireContext()).apply {
            this.text = text
            setTextColor(resources.getColor(com.zodiacguard.app.R.color.app_text, null))
            textSize = 14f
            val verticalPadding = (12 * resources.displayMetrics.density).toInt()
            setPadding(0, verticalPadding, 0, verticalPadding)
        }
        binding.layoutSpecialDays.addView(row)
    }

    private fun formatMinutes(minutes: Int): String = "${minutes / 60}h ${minutes % 60}m"

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
