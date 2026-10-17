package com.zodiacguard.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.zodiacguard.app.data.ContractDataStore
import com.zodiacguard.app.databinding.FragmentSettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var contractDataStore: ContractDataStore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        contractDataStore = ContractDataStore(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Carica i valori salvati all'apertura
        viewLifecycleOwner.lifecycleScope.launch {
            contractDataStore.weeklyHoursFlow.collectLatest { hours ->
                binding.etWeeklyHours.setText(hours.toString())
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            contractDataStore.workDaysFlow.collectLatest { days ->
                binding.etWorkDays.setText(days.toString())
            }
        }

        // 2. Salva i valori quando premi il bottone
        binding.btnSaveContract.setOnClickListener {
            val hours = binding.etWeeklyHours.text.toString().toIntOrNull() ?: 38
            val days = binding.etWorkDays.text.toString().toIntOrNull() ?: 6

            viewLifecycleOwner.lifecycleScope.launch {
                contractDataStore.saveContract(hours, days)
                Toast.makeText(requireContext(), "Parametri salvati! ️", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
