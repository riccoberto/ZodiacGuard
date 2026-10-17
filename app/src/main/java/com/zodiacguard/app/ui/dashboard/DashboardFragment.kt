package com.zodiacguard.app.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.zodiacguard.app.R
import com.zodiacguard.app.data.StatsHelper
import com.zodiacguard.app.data.WorkDataStore
import com.zodiacguard.app.data.WorkEntry
import com.zodiacguard.app.databinding.FragmentDashboardBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var workDataStore: WorkDataStore
    private var selectedActivity: String = "Servizio"
    private var selectedLocation: String = "Modena"
    private var selectedDate: String = "" // Data selezionata
    private var currentEntries: List<WorkEntry> = emptyList()
    private lateinit var entryAdapter: EntryAdapter

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isAdded) {
                updateStatsUI()
                binding.root.postDelayed(this, 60000)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        workDataStore = WorkDataStore(requireContext())
        selectedDate = WorkEntry.getCurrentDate()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateDateDisplay()

        setupSpinner(binding.spinnerActivity, R.array.activity_types) { 
            selectedActivity = it
            updateStatsUI()
        }
        setupSpinner(binding.spinnerLocation, R.array.locations) { 
            selectedLocation = it
            updateStatsUI()
        }

        entryAdapter = EntryAdapter(emptyList(), 
            onEditClick = { entry -> showAddEditDialog(entry) },
            onDeleteClick = { entry -> deleteEntry(entry) }
        )
        binding.recyclerViewEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewEntries.adapter = entryAdapter

        // Osserva le marcature della data selezionata
        viewLifecycleOwner.lifecycleScope.launch {
            workDataStore.getEntriesForDate(selectedDate).collectLatest { entries ->
                currentEntries = entries
                entryAdapter.updateData(entries)
                updateStatsUI()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            workDataStore.getNoteForDate(selectedDate).collectLatest { note ->
                if (binding.etDailyNotes.text.toString() != note) binding.etDailyNotes.setText(note)
            }
        }

        binding.etDailyNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewLifecycleOwner.lifecycleScope.launch { workDataStore.saveNote(selectedDate, s.toString()) }
            }
        })

        binding.btnCheckIn.setOnClickListener { showTimePickerAndRegister("ENTRATA") }
        binding.btnCheckOut.setOnClickListener { showTimePickerAndRegister("USCITA") }
        
        // NUOVO: Selettore data
        binding.btnChangeDate.setOnClickListener { showDatePicker() }

        binding.root.postDelayed(updateRunnable, 60000)
    }

    private fun updateDateDisplay() {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)
        binding.tvDate.text = if (date != null) sdf.format(date) else selectedDate
    }

    // NUOVO: Mostra il selettore di data
    private fun showDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Seleziona data")
            .setSelection(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .parse(selectedDate)?.time ?: System.currentTimeMillis()
            )
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            selectedDate = sdf.format(Date(selection))
            updateDateDisplay()
            
            // Ricarica le marcature per la nuova data
            viewLifecycleOwner.lifecycleScope.launch {
                workDataStore.getEntriesForDate(selectedDate).collectLatest { entries ->
                    currentEntries = entries
                    entryAdapter.updateData(entries)
                    updateStatsUI()
                }
            }
        }

        datePicker.show(parentFragmentManager, "date_picker")
    }

    private fun setupSpinner(spinner: android.widget.Spinner, arrayResId: Int, onSelect: (String) -> Unit) {
        val adapter = ArrayAdapter.createFromResource(requireContext(), arrayResId, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                onSelect(parent.getItemAtPosition(position).toString())
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun showTimePickerAndRegister(type: String) {
        val calendar = Calendar.getInstance()
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(calendar.get(Calendar.HOUR_OF_DAY))
            .setMinute(calendar.get(Calendar.MINUTE))
            .setTitleText("Orario $type")
            .build()

        picker.addOnPositiveButtonClickListener {
            val newCal = Calendar.getInstance()
            newCal.set(Calendar.HOUR_OF_DAY, picker.hour)
            newCal.set(Calendar.MINUTE, picker.minute)
            newCal.set(Calendar.SECOND, 0)
            newCal.set(Calendar.MILLISECOND, 0)

            val entry = WorkEntry(newCal.timeInMillis, type, selectedActivity, selectedLocation, selectedDate)
            viewLifecycleOwner.lifecycleScope.launch {
                workDataStore.addEntry(entry)
                Toast.makeText(requireContext(), "$type registrata alle ${picker.hour}:${picker.minute}", Toast.LENGTH_SHORT).show()
            }
        }
        picker.show(parentFragmentManager, "time_picker_$type")
    }

    private fun deleteEntry(entry: WorkEntry) {
        viewLifecycleOwner.lifecycleScope.launch {
            workDataStore.deleteEntry(entry)
            Toast.makeText(requireContext(), "Marcatura cancellata", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddEditDialog(existingEntry: WorkEntry?) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = existingEntry!!.timestamp
        
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(calendar.get(Calendar.HOUR_OF_DAY))
            .setMinute(calendar.get(Calendar.MINUTE))
            .setTitleText("Modifica Orario")
            .build()

        picker.addOnPositiveButtonClickListener {
            val newCal = Calendar.getInstance()
            newCal.set(Calendar.HOUR_OF_DAY, picker.hour)
            newCal.set(Calendar.MINUTE, picker.minute)
            newCal.set(Calendar.SECOND, 0)
            newCal.set(Calendar.MILLISECOND, 0)

            val newEntry = WorkEntry(newCal.timeInMillis, existingEntry.type, existingEntry.activity, existingEntry.location, selectedDate)

            viewLifecycleOwner.lifecycleScope.launch {
                workDataStore.deleteEntry(existingEntry)
                workDataStore.addEntry(newEntry)
            }
        }
        picker.show(parentFragmentManager, "time_picker_edit")
    }

    private fun updateStatsUI() {
        val stats = StatsHelper.calculateDailyStats(currentEntries, selectedActivity, System.currentTimeMillis())
        
        binding.tvTotalHours.text = StatsHelper.minutesToText(stats.totalMinutes)
        binding.tvOrdinaryLabel.text = "Ordinarie: ${StatsHelper.minutesToText(stats.ordinaryMinutes)}"
        binding.tvTravelTime.text = "Ore viaggio: ${StatsHelper.minutesToText(stats.travelMinutes)}"
        
        val extraColor: Int
        val extraLabel: String
        
        if (stats.isOvertime) {
            extraColor = Color.parseColor("#C41E3A")
            extraLabel = "Straordinari: ${StatsHelper.minutesToText(stats.extraMinutes)}"
        } else {
            extraColor = Color.parseColor("#4A90E2")
            extraLabel = "Banca Ore: ${StatsHelper.minutesToText(stats.extraMinutes)}"
        }
        
        binding.tvExtraLabel.text = extraLabel
        binding.tvExtraLabel.setTextColor(extraColor)
        updateProgressBar(stats)
    }

    // NUOVO: Barra con linea di riferimento
    private fun updateProgressBar(stats: StatsHelper.DailyStats) {
        val total = stats.totalMinutes
        if (total == 0) {
            binding.barOrdinary.layoutParams.width = 0
            binding.barExtra.layoutParams.width = 0
            binding.barCurrentTime.layoutParams.width = 0
            return
        }
        
        // Calcola posizione della linea (minuti attuali / target)
        val targetMinutes = StatsHelper.DAILY_TARGET_MINUTES
        val currentProgress = (stats.currentMinutes.toFloat() / targetMinutes).coerceIn(0f, 1f)
        
        val ordinaryParams = binding.barOrdinary.layoutParams as LinearLayout.LayoutParams
        ordinaryParams.weight = stats.ordinaryMinutes.toFloat()
        binding.barOrdinary.layoutParams = ordinaryParams
        
        val extraParams = binding.barExtra.layoutParams as LinearLayout.LayoutParams
        extraParams.weight = stats.extraMinutes.toFloat()
        binding.barExtra.layoutParams = extraParams
        
        val extraColor = if (stats.isOvertime) Color.parseColor("#C41E3A") else Color.parseColor("#4A90E2")
        binding.barExtra.setBackgroundColor(extraColor)
        
        // Posiziona la linea bianca
        val currentTimeParams = binding.barCurrentTime.layoutParams as FrameLayout.LayoutParams
        currentTimeParams.marginStart = (currentProgress * resources.displayMetrics.widthPixels * 0.9).toInt()
        binding.barCurrentTime.layoutParams = currentTimeParams
    }

    override fun onDestroyView() { 
        super.onDestroyView()
        binding.root.removeCallbacks(updateRunnable)
        _binding = null 
    }
}
