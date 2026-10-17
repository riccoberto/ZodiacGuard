package com.zodiacguard.app.ui.dashboard

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        workDataStore = WorkDataStore(requireContext())
        todayDate = WorkEntry.getCurrentDate()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Imposta la data
        binding.tvDate.text = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())

        // 2. Configura Spinner Attività
        val activityAdapter = ArrayAdapter.createFromResource(
            requireContext(), R.array.activity_types, android.R.layout.simple_spinner_item
        )
        activityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerActivity.adapter = activityAdapter
        binding.spinnerActivity.setSelection(0) // Default: Servizio
        binding.spinnerActivity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedActivity = parent.getItemAtPosition(position).toString()
                updateStatsUI() // Aggiorna la barra se cambia l'attività
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // 3. Configura Spinner Sede
        val locationAdapter = ArrayAdapter.createFromResource(
            requireContext(), R.array.locations, android.R.layout.simple_spinner_item
        )
        locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLocation.adapter = locationAdapter
        binding.spinnerLocation.setSelection(0) // Default: Modena
        binding.spinnerLocation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedLocation = parent.getItemAtPosition(position).toString()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // 4. Osserva le marcature di oggi e aggiorna l'UI
        viewLifecycleOwner.lifecycleScope.launch {
            workDataStore.getEntriesForDate(todayDate).collectLatest { entries ->
                updateEntriesList(entries)
                updateStatsUI()
            }
        }

        // 5. Osserva le note di oggi
        viewLifecycleOwner.lifecycleScope.launch {
            workDataStore.getNoteForDate(todayDate).collectLatest { note ->
                if (binding.etDailyNotes.text.toString() != note) {
                    binding.etDailyNotes.setText(note)
                }
            }
        }

        // 6. Salva le note quando l'utente scrive
        binding.etDailyNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewLifecycleOwner.lifecycleScope.launch {
                    workDataStore.saveNote(todayDate, s.toString())
                }
            }
        })

        // 7. Logica Pulsante ENTRATA
        binding.btnCheckIn.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val entry = WorkEntry(
                    timestamp = System.currentTimeMillis(),
                    type = "ENTRATA",
                    activity = selectedActivity,
                    location = selectedLocation,
                    date = todayDate
                )
                workDataStore.addEntry(entry)
                Toast.makeText(requireContext(), "Entrata registrata!", Toast.LENGTH_SHORT).show()
            }
        }

        // 8. Logica Pulsante USCITA
        binding.btnCheckOut.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val entry = WorkEntry(
                    timestamp = System.currentTimeMillis(),
                    type = "USCITA",
                    activity = selectedActivity,
                    location = selectedLocation,
                    date = todayDate
                )
                workDataStore.addEntry(entry)
                Toast.makeText(requireContext(), "Uscita registrata!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateEntriesList(entries: List<WorkEntry>) {
        if (entries.isEmpty()) {
            binding.tvEntriesList.text = "Nessuna marcatura oggi."
            return
        }
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        entries.sortedBy { it.timestamp }.forEach { entry ->
            sb.append("${sdf.format(Date(entry.timestamp))} - ${entry.type} | ${entry.activity} | ${entry.location}\n")
        }
        binding.tvEntriesList.text = sb.toString().trimEnd()
    }

    private fun updateStatsUI() {
        // Qui dovremmo ricalcolare le stats in base alle entry correnti.
        // Per semplicità, in questa versione iniziale, la barra si aggiorna quando cambiano le entry.
        // Un'implementazione completa richiederebbe di salvare l'ultima lista di entry in una variabile.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
