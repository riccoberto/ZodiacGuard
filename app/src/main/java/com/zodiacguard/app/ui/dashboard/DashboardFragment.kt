package com.zodiacguard.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        // 1. Ascolta i cambiamenti dell'Entrata
        viewLifecycleOwner.lifecycleScope.launch {
            workDataStore.checkInFlow.collectLatest { checkIn ->
                if (checkIn != null) {
                    binding.btnCheckIn.isEnabled = false
                    binding.btnCheckIn.text = "ENTRATO ALLE $checkIn"
                    binding.btnCheckOut.isEnabled = true
                } else {
                    binding.btnCheckIn.isEnabled = true
                    binding.btnCheckIn.text = "ENTRATA"
                    binding.btnCheckOut.isEnabled = false
                }
            }
        }

        // 2. Ascolta i cambiamenti dell'Uscita
        viewLifecycleOwner.lifecycleScope.launch {
            workDataStore.checkOutFlow.collectLatest { checkOut ->
                if (checkOut != null) {
                    binding.btnCheckOut.isEnabled = false
                    binding.btnCheckOut.text = "USCITO ALLE $checkOut"
                } else {
                    binding.btnCheckOut.isEnabled = binding.btnCheckIn.isEnabled.not()
                }
            }
        }

        // 3. Logica Pulsante ENTRATA
        binding.btnCheckIn.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val currentTime = getCurrentTime()
                workDataStore.saveCheckIn(currentTime)
                Toast.makeText(requireContext(), "Entrata registrata alle $currentTime", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Logica Pulsante USCITA
        binding.btnCheckOut.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val currentTime = getCurrentTime()
                workDataStore.saveCheckOut(currentTime)
                Toast.makeText(requireContext(), "Uscita registrata alle $currentTime", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. NUOVO: Logica Pulsante IMPOSTAZIONI
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
