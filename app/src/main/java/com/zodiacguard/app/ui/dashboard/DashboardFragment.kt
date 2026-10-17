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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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

        // Setup Spinners
        setupSpinner(binding.spinnerActivity, R.array.activity_types) { selectedActivity = it }
        setupSpinner(binding.spinnerLocation, R.array.locations) { selectedLocation = it }

        // Setup RecyclerView
        entryAdapter = EntryAdapter(emptyList(), 
            onEditClick = { entry -> showAddEditDialog(entry) },
            onDeleteClick = { entry -> deleteEntry(entry) }
        )
        binding.recyclerViewEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewEntries.adapter = entryAdapter

        // Osserva dati
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

        // Pulsanti principali
        binding.btnCheckIn.setOnClickListener { registerEntry("ENTRATA") }
        binding.btnCheckOut.setOnClickListener { registerEntry("USCITA") }
        
        // NUOVO: Pulsante Aggiungi a mano
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

    // Finestra per aggiungere o modificare a mano
    private fun showAddEditDialog(existingEntry: WorkEntry?) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(if (existingEntry == null) "Aggiungi Marcatura" else "Modifica Marcatura")

        val input = EditText(requireContext())
        input.hint = "Orario (es. 09:00)"
        if (existingEntry != null) {
            input.setText(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(existingEntry.timestamp)))
        }
        builder.setView(input)

        val types = arrayOf("ENTRATA", "USCITA")
        var selectedType = existingEntry?.type ?: "ENTRATA"
        builder.setSingleChoiceItems(types, if (selectedType == "ENTRATA") 0 else 1) { _, which -> selectedType = types[which] }

        builder.setPositiveButton("SALVA") { dialog, _ ->
            val timeStr = input.text.toString()
            try {
                val sdfIn = SimpleDateFormat("HH:mm", Locale.getDefault())
                val date = sdfIn.parse(timeStr)
                val calendar = Calendar.getInstance()
                if (date != null) {
                    calendar.time = date
                    val todayCal = Calendar.getInstance()
                    calendar.set(Calendar.YEAR, todayCal.get(Calendar.YEAR))
                    calendar.set(Calendar.MONTH, todayCal.get(Calendar.MONTH))
                    calendar.set(Calendar.DAY_OF_MONTH, todayCal.get(Calendar.DAY_OF_MONTH))

                    val newTimestamp = calendar.timeInMillis
                    val activity = existingEntry?.activity ?: selectedActivity
                    val location = existingEntry?.location ?: selectedLocation
                    val newEntry = WorkEntry(newTimestamp, selectedType, activity, location, todayDate)

                    viewLifecycleOwner.lifecycleScope.launch {
                        if (existingEntry != null) workDataStore.deleteEntry(existingEntry)
                        workDataStore.addEntry(newEntry)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Formato ora errato (usa HH:mm)", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("ANNULLA") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun updateStatsUI() {
        val stats = StatsHelper.calculateDailyStats(currentEntries)
        binding.tvTotalHours.text = StatsHelper.minutesToText(stats.totalMinutes)
        binding.tvOrdinaryLabel.text = "Ordinarie: ${StatsHelper.minutesToText(stats.ordinaryMinutes)}"
        
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
