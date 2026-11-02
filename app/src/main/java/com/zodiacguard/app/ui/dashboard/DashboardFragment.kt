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
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.zodiacguard.app.R
import com.zodiacguard.app.data.StatsHelper
import com.zodiacguard.app.data.WorkDataStore
import com.zodiacguard.app.data.WorkEntry
import com.zodiacguard.app.databinding.FragmentDashboardBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var workDataStore: WorkDataStore
    private var selectedActivity: String = "Servizio"
    private var previousActivity: String = "Servizio" // Attività non-produzione precedente
    private var selectedLocation: String = "Modena"
    private var selectedDate: String = ""
    private var isProductionMode: Boolean = false
    private var updatingDayModeSelection = false
    private var dayModeReady = false
    private var displayedDayMode = ""
    private var currentEntries: List<WorkEntry> = emptyList()
    private lateinit var entryAdapter: EntryAdapter

    private var dataJob: Job? = null

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

        setupSpinner(binding.spinnerActivity, R.array.activity_types) { newActivity ->
            if (newActivity != selectedActivity && !isProductionMode) {
                selectedActivity = newActivity
                previousActivity = newActivity // Salva come fallback
                viewLifecycleOwner.lifecycleScope.launch {
                    workDataStore.updateAllEntriesActivity(selectedDate, newActivity)
                    Toast.makeText(requireContext(), "Attività aggiornata per tutte le marcature", Toast.LENGTH_SHORT).show()
                }
            }
            updateStatsUI()
        }
        setupSpinner(binding.spinnerLocation, R.array.locations) { newLocation ->
            if (newLocation != selectedLocation) {
                selectedLocation = newLocation
                viewLifecycleOwner.lifecycleScope.launch {
                    workDataStore.updateAllEntriesLocation(selectedDate, newLocation)
                    val travelText = when(newLocation) {
                        "Vignola", "Castelfranco" -> "1h 30m di viaggio aggiunte"
                        "Bologna" -> "2h 00m di viaggio aggiunte"
                        else -> "Viaggio rimosso"
                    }
                    Toast.makeText(requireContext(), "Sede aggiornata. $travelText", Toast.LENGTH_LONG).show()
                }
            }
            updateStatsUI()
        }

        binding.spinnerDayMode.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val mode = parent.getItemAtPosition(position).toString()
                    if (dayModeReady && !updatingDayModeSelection && mode != displayedDayMode) {
                        displayedDayMode = mode
                        selectDayMode(mode)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) = Unit
            }

        entryAdapter = EntryAdapter(emptyList(), 
            onEditClick = { entry -> showAddEditDialog(entry) },
            onDeleteClick = { entry -> deleteEntry(entry) }
        )
        binding.recyclerViewEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewEntries.adapter = entryAdapter

        startObserving()

        binding.etDailyNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewLifecycleOwner.lifecycleScope.launch { workDataStore.saveNote(selectedDate, s.toString()) }
            }
        })

        binding.switchIncludeNoteInPdf.setOnCheckedChangeListener { _, included ->
            viewLifecycleOwner.lifecycleScope.launch {
                workDataStore.setNoteIncludedInPdf(selectedDate, included)
            }
        }

        binding.btnCheckIn.setOnClickListener { showTimePickerAndRegister("ENTRATA") }
        binding.btnCheckOut.setOnClickListener { showTimePickerAndRegister("USCITA") }
        binding.btnChangeDate.setOnClickListener { showCustomCalendar() }
        
        binding.btnPrevDay.setOnClickListener { changeDay(-1) }
        binding.btnNextDay.setOnClickListener { changeDay(1) }
        
        binding.btnToday.setOnClickListener {
            selectedDate = WorkEntry.getCurrentDate()
            updateDateDisplay()
            startObserving()
            Toast.makeText(requireContext(), "Tornato alla data odierna", Toast.LENGTH_SHORT).show()
        }

        binding.root.postDelayed(updateRunnable, 60000)
    }

    private fun changeDay(offset: Int) {
        val cal = Calendar.getInstance()
        cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate) ?: Date()
        cal.add(Calendar.DAY_OF_MONTH, offset)
        selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        updateDateDisplay()
        startObserving()
    }

    private fun showCustomCalendar() {
        viewLifecycleOwner.lifecycleScope.launch {
            val allEntries = workDataStore.entriesFlow.first()
            val datesWithData = allEntries.map { it.date }.toSet()
            
            val dialog = CustomCalendarDialog(
                selectedDate,
                datesWithData
            ) { newDate ->
                selectedDate = newDate
                updateDateDisplay()
                startObserving()
            }
            dialog.show(parentFragmentManager, "custom_calendar")
        }
    }

    private fun startObserving() {
        dayModeReady = false
        dataJob?.cancel()
        dataJob = viewLifecycleOwner.lifecycleScope.launch {
            launch {
                val date = selectedDate
                combine(
                    workDataStore.getEntriesForDate(date),
                    workDataStore.getProductionModeForDate(date)
                ) { entries, isProduction -> entries to isProduction }
                    .collectLatest { (entries, isProduction) ->
                    currentEntries = entries
                    isProductionMode = isProduction
                    entryAdapter.updateData(entries)
                    dayModeReady = true
                    updateDayModeSelection()
                    updateStatsUI()
                }
            }
            launch {
                workDataStore.getNoteForDate(selectedDate).collectLatest { note ->
                    if (binding.etDailyNotes.text.toString() != note) binding.etDailyNotes.setText(note)
                }
            }
            launch {
                workDataStore.isNoteIncludedInPdf(selectedDate).collectLatest { included ->
                    if (binding.switchIncludeNoteInPdf.isChecked != included) {
                        binding.switchIncludeNoteInPdf.isChecked = included
                    }
                }
            }
        }
    }

    private fun updateDayModeSelection() {
        val mode = when {
            currentEntries.any { it.activity == "Ferie" } -> "Ferie"
            currentEntries.any { it.activity == "Malattia" } -> "Malattia"
            isProductionMode -> "Produzione"
            else -> "Ordinaria"
        }
        val positions = mapOf(
            "Ordinaria" to 0,
            "Produzione" to 1,
            "Ferie" to 2,
            "Malattia" to 3
        )
        val position = positions.getValue(mode)
        displayedDayMode = mode
        if (binding.spinnerDayMode.selectedItemPosition != position) {
            updatingDayModeSelection = true
            binding.spinnerDayMode.setSelection(position)
            updatingDayModeSelection = false
        }
        val isAbsence = mode == "Ferie" || mode == "Malattia"
        binding.spinnerActivity.isEnabled = mode == "Ordinaria"
        binding.spinnerActivity.alpha = if (mode == "Ordinaria") 1f else 0.5f
        binding.btnCheckIn.isEnabled = !isAbsence
        binding.btnCheckOut.isEnabled = !isAbsence
        if (mode == "Produzione") selectedActivity = "Produzione"
    }

    private fun selectDayMode(mode: String) {
        when (mode) {
            "Ferie", "Malattia" -> selectAbsenceMode(mode)
            "Produzione" -> {
                if (selectedActivity != "Produzione") previousActivity = selectedActivity
                selectedActivity = "Produzione"
                isProductionMode = true
                viewLifecycleOwner.lifecycleScope.launch {
                    workDataStore.saveProductionMode(selectedDate, true)
                    workDataStore.updateAllEntriesActivity(selectedDate, "Produzione")
                }
            }
            else -> {
                isProductionMode = false
                val isAbsenceDay = currentEntries.any {
                    it.activity == "Ferie" || it.activity == "Malattia"
                }
                if (isAbsenceDay) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Rimuovere la giornata?")
                        .setMessage("Le marcature automatiche di ferie o malattia verranno rimosse.")
                        .setNegativeButton("Annulla") { _, _ -> updateDayModeSelection() }
                        .setPositiveButton("Rimuovi") { _, _ ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                workDataStore.replaceEntriesForDate(selectedDate, emptyList())
                                workDataStore.saveProductionMode(selectedDate, false)
                            }
                        }
                        .show()
                } else {
                    selectedActivity =
                        if (previousActivity == "Produzione") "Servizio" else previousActivity
                    viewLifecycleOwner.lifecycleScope.launch {
                        workDataStore.saveProductionMode(selectedDate, false)
                        workDataStore.updateAllEntriesActivity(selectedDate, selectedActivity)
                    }
                }
            }
        }
    }

    private fun selectAbsenceMode(mode: String) {
        val alreadySelected = currentEntries.size == 2 &&
            currentEntries.all { it.activity == mode }
        if (alreadySelected) return

        val dateKey = selectedDate
        val applyMode = {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(dateKey)
                ?: throw IllegalArgumentException("Data selezionata non valida")
            val entries = listOf("ENTRATA" to 9 * 60, "USCITA" to 15 * 60 + 25)
                .map { (type, minutes) ->
                    val calendar = Calendar.getInstance().apply {
                        time = date
                        set(Calendar.HOUR_OF_DAY, minutes / 60)
                        set(Calendar.MINUTE, minutes % 60)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    WorkEntry(
                        calendar.timeInMillis,
                        type,
                        mode,
                        "Modena",
                        dateKey
                    )
                }
            viewLifecycleOwner.lifecycleScope.launch {
                workDataStore.saveProductionMode(dateKey, false)
                workDataStore.replaceEntriesForDate(dateKey, entries)
            }
        }

        if (currentEntries.isEmpty()) {
            applyMode()
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle("Sostituire le marcature?")
                .setMessage("Le marcature già presenti nel giorno verranno sostituite con Ferie/Malattia 09:00–15:25.")
                .setNegativeButton("Annulla") { _, _ -> updateDayModeSelection() }
                .setPositiveButton("Sostituisci") { _, _ -> applyMode() }
                .show()
        }
    }

    private fun updateDateDisplay() {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)
        binding.tvDate.text = if (date != null) sdf.format(date) else selectedDate
    }

    private fun setupSpinner(spinner: android.widget.Spinner, arrayResId: Int, onSelect: (String) -> Unit) {
        val adapter = ArrayAdapter.createFromResource(requireContext(), arrayResId, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) { onSelect(parent.getItemAtPosition(position).toString()) }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun showTimePickerAndRegister(type: String) {
        val calendar = Calendar.getInstance()
        val picker = MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H).setHour(calendar.get(Calendar.HOUR_OF_DAY)).setMinute(calendar.get(Calendar.MINUTE)).setTitleText("Orario $type").build()
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
        val picker = MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H).setHour(calendar.get(Calendar.HOUR_OF_DAY)).setMinute(calendar.get(Calendar.MINUTE)).setTitleText("Modifica Orario").build()
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
        val stats = StatsHelper.calculateDailyStats(currentEntries, isProductionMode, System.currentTimeMillis())
        
        binding.tvTotalHours.text = StatsHelper.minutesToText(stats.totalMinutes)
        binding.tvOrdinaryLabel.text = "Ordinarie: ${StatsHelper.minutesToText(stats.ordinaryMinutes)}"
        binding.tvTravelTime.text = "Viaggio: ${StatsHelper.minutesToText(stats.travelMinutes)}"
        
        val extraColor: Int
        val extraLabel: String
        
        if (stats.isOvertime) {
            extraColor = Color.parseColor("#FF5252")
            extraLabel = "Straordinari: ${StatsHelper.minutesToText(stats.extraMinutes)}"
        } else {
            extraColor = Color.parseColor("#4A90E2")
            extraLabel = "Banca Ore: ${StatsHelper.minutesToText(stats.extraMinutes)}"
        }
        
        binding.tvExtraLabel.text = extraLabel
        binding.tvExtraLabel.setTextColor(extraColor)
        updateProgressBar(stats)
    }

    private fun updateProgressBar(stats: StatsHelper.DailyStats) {
        binding.progressBarContainer.post {
            val containerWidth = binding.progressBarContainer.width
            if (containerWidth > 0) {
                val today = WorkEntry.getCurrentDate()
                if (selectedDate == today) {
                    val targetMinutes = StatsHelper.DAILY_TARGET_MINUTES
                    val progress = (stats.totalMinutes.toFloat() / targetMinutes).coerceIn(0f, 1f)
                    val marginStart = (progress * containerWidth).toInt()
                    
                    val params = binding.barCurrentTime.layoutParams as FrameLayout.LayoutParams
                    params.marginStart = marginStart
                    binding.barCurrentTime.layoutParams = params
                    binding.barCurrentTime.visibility = View.VISIBLE
                } else {
                    binding.barCurrentTime.visibility = View.GONE
                }
            }
            
            val total = stats.totalMinutes
            if (total == 0) {
                // FIX: Reset barra mantenendo l'altezza originale
                val ordinaryParams = binding.barOrdinary.layoutParams as LinearLayout.LayoutParams
                ordinaryParams.weight = 0f
                binding.barOrdinary.layoutParams = ordinaryParams
                
                val travelParams = binding.barTravel.layoutParams as LinearLayout.LayoutParams
                travelParams.weight = 0f
                binding.barTravel.layoutParams = travelParams
                
                val extraParams = binding.barExtra.layoutParams as LinearLayout.LayoutParams
                extraParams.weight = 0f
                binding.barExtra.layoutParams = extraParams
                
                // Mostra solo la barra ordinaria (gialla) come placeholder
                binding.barOrdinary.setBackgroundColor(Color.parseColor("#FFD700"))
                binding.barTravel.setBackgroundColor(Color.parseColor("#B388FF"))
                binding.barExtra.setBackgroundColor(Color.parseColor("#4A90E2"))
                return@post
            }
            
            val workMinutes = stats.totalMinutes - stats.travelMinutes - stats.extraMinutes
            val safeWork = if (workMinutes < 0) 0 else workMinutes
            
            val ordinaryParams = binding.barOrdinary.layoutParams as LinearLayout.LayoutParams
            ordinaryParams.weight = safeWork.toFloat()
            binding.barOrdinary.layoutParams = ordinaryParams
            
            val travelParams = binding.barTravel.layoutParams as LinearLayout.LayoutParams
            travelParams.weight = stats.travelMinutes.toFloat()
            binding.barTravel.layoutParams = travelParams
            
            val extraParams = binding.barExtra.layoutParams as LinearLayout.LayoutParams
            extraParams.weight = stats.extraMinutes.toFloat()
            binding.barExtra.layoutParams = extraParams
            
            val extraColor = if (stats.isOvertime) Color.parseColor("#FF5252") else Color.parseColor("#4A90E2")
            binding.barExtra.setBackgroundColor(extraColor)
            binding.barTravel.setBackgroundColor(Color.parseColor("#B388FF"))
            binding.barOrdinary.setBackgroundColor(Color.parseColor("#FFD700"))
        }
    }

    override fun onDestroyView() { 
        super.onDestroyView()
        binding.root.removeCallbacks(updateRunnable)
        dataJob?.cancel()
        _binding = null 
    }
}
