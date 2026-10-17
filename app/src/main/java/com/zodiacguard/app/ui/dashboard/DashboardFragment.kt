package com.zodiacguard.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.zodiacguard.app.R
import com.zodiacguard.app.data.WorkDataStore
import com.zodiacguard.app.databinding.FragmentDashboardBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var workDataStore: WorkDataStore
    private var selectedCategory: String = "Ordinario"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        workDataStore = WorkDataStore(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Configura lo Spinner delle categorie
        val adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.work_categories,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = adapter

        binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedCategory = parent.getItemAtPosition(position).toString()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // 2. Ascolta e mostra il registro giornaliero
        viewLifecycleOwner.lifecycleScope.launch {
            workDataStore.dailyLogFlow.collectLatest { log ->
                binding.tvDailyLog.text = if (log.isEmpty()) "Nessun registro per oggi." else log
            }
        }

        // 3. Logica Pulsante ENTRATA
        binding.btnCheckIn.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val currentTime = getCurrentTime()
                workDataStore.addLogEntry(currentTime, "ENTRATA", selectedCategory)
                Toast.makeText(requireContext(), "Entrata registrata: $selectedCategory", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Logica Pulsante USCITA
        binding.btnCheckOut.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val currentTime = getCurrentTime()
                workDataStore.addLogEntry(currentTime, "USCITA", selectedCategory)
                Toast.makeText(requireContext(), "Uscita registrata: $selectedCategory", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Logica Pulsante IMPOSTAZIONI
        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }
    }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
