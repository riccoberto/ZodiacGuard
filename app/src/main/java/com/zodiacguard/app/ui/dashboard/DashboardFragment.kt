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
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var workDataStore: WorkDataStore
    private var selectedActivity: String = "Servizio"
    private var selectedLocation: String = "Modena"
    private var todayDate: String = ""
    private var currentEntries: List<WorkEntry> = emptyList()
    private lateinit var entryAdapter: EntryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        workDataStore = WorkDataStore(requireContext())
        todayDate = WorkEntry.getCurrentDate()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvDate.text = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())

        setupSpinner(binding.spinnerActivity, R.array.activity_types) { 
            selectedActivity = it
            updateStatsUI() // Aggiorna i colori subito se cambi attività
        }
        setupSpinner(binding.spinnerLocation, R.array.locations) { selectedLocation = it }

        entryAdapter = EntryAdapter(emptyList(), 
            onEditClick = { entry -> showAddEditDialog(entry) },
            onDeleteClick = { entry -> deleteEntry(entry) }
        )
        binding.recyclerViewEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewEntries.adapter = entryAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            workDataStore.getEntriesForDate(todayDate).collectLatest { entries ->
                currentEntries = entries
                entryAdapter.updateData(entries)
                updateStatsUI()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            workDataStore.getNoteForDate(todayDate).collectLatest { note ->
                if (binding.etDailyNotes.text.toString() != note) binding.etDailyNotes.setText(note)
            }
        }

        binding.etDailyNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewLifecycleOwner.lifecycleScope.launch { workDataStore.saveNote(todayDate, s.toString()) }
            }
        })

        binding.btnCheckIn.setOnClickListener { registerEntry("ENTRATA") }
        binding.btnCheckOut.setOnClickListener { registerEntry("USCITA") }
        binding.btnAddManual.setOnClickListener { showAddEditDialog(null) }
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

    private fun registerEntry(type: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val entry = WorkEntry(System.currentTimeMillis(), type, selectedActivity, selectedLocation, todayDate)
            workDataStore.addEntry(entry)
            Toast.makeText(requireContext(), "$type registrata!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteEntry(entry: WorkEntry) {
        viewLifecycleOwner.lifecycleScope.launch {
            workDataStore.deleteEntry(entry)
            Toast.makeText(requireContext(), "Marcatura cancellata", Toast.LENGTH_SHORT).show()
        }
    }

    // NUOVA FUNZIONE CON L'OROLOGIO A RUOTA
    private fun showAddEditDialog(existingEntry: WorkEntry?) {
        val isEdit = existingEntry != null
        
        // Prepara l'orologio
        val calendar = Calendar.getInstance()
        if (isEdit) calendar.timeInMillis = existingEntry!!.timestamp
        
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(calendar.get(Calendar.HOUR_OF_DAY))
            .setMinute(calendar.get(Calendar.MINUTE))
            .setTitleText(if (isEdit) "Modifica Orario" else "Nuovo Orario")
            .build()

        picker.addOnPositiveButtonClickListener {
            val hour = picker.hour
            val minute = picker.minute
            
            val newCal = Calendar.getInstance()
            newCal.set(Calendar.HOUR_OF_DAY, hour)
            newCal.set(Calendar.MINUTE, minute)
            newCal.set(Calendar.SECOND, 0)
            newCal.set(Calendar.MILLISECOND, 0)

            val newTimestamp = newCal.timeInMillis
            val type = existingEntry?.type ?: "ENTRATA" // Semplificato per ora
            val activity = existingEntry?.activity ?: selectedActivity
            val location = existingEntry?.location ?: selectedLocation
            
            val newEntry = WorkEntry(newTimestamp, type, activity, location, todayDate)

            viewLifecycleOwner.lifecycleScope.launch {
                if (isEdit) workDataStore.deleteEntry(existingEntry!!)
                workDataStore.addEntry(newEntry)
            }
        }
        
        // Se è una modifica, mostriamo anche un tasto per cambiare tipo (Entrata/Uscita) in un secondo momento, per ora teniamo semplice
        picker.show(parentFragmentManager, "time_picker")
    }

    private fun updateStatsUI() {
        // PASSIAMO selectedActivity AL CALCOLATORE
        val stats = StatsHelper.calculateDailyStats(currentEntries, selectedActivity)
        
        binding.tvTotalHours.text = StatsHelper.minutesToText(stats.totalMinutes)
        binding.tvOrdinaryLabel.text = "Ordinarie: ${StatsHelper.minutesToText(stats.ordinaryMinutes)}"
        
        val extraColor: Int
        val extraLabel: String
        
        if (stats.isOvertime) {
            extraColor = Color.parseColor("#C41E3A") // Rosso
            extraLabel = "Straordinari: ${StatsHelper.minutesToText(stats.extraMinutes)}"
        } else {
            extraColor = Color.parseColor("#4A90E2") // Blu
            extraLabel = "Banca Ore: ${StatsHelper.minutesToText(stats.extraMinutes)}"
        }
        
        binding.tvExtraLabel.text = extraLabel
        binding.tvExtraLabel.setTextColor(extraColor)
        updateProgressBar(stats.ordinaryMinutes, stats.extraMinutes, extraColor)
    }

    private fun updateProgressBar(ordinaryMinutes: Int, extraMinutes: Int, extraColor: Int) {
        val total = ordinaryMinutes + extraMinutes
        if (total == 0) {
            binding.barOrdinary.layoutParams.width = 0
            binding.barExtra.layoutParams.width = 0
            return
        }
        val ordinaryParams = binding.barOrdinary.layoutParams as LinearLayout.LayoutParams
        ordinaryParams.weight = ordinaryMinutes.toFloat()
        binding.barOrdinary.layoutParams = ordinaryParams
        
        val extraParams = binding.barExtra.layoutParams as LinearLayout.LayoutParams
        extraParams.weight = extraMinutes.toFloat()
        binding.barExtra.layoutParams = extraParams
        binding.barExtra.setBackgroundColor(extraColor)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
