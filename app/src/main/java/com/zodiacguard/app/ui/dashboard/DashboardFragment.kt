package com.zodiacguard.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.zodiacguard.app.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Logica per il pulsante ENTRATA
        binding.btnCheckIn.setOnClickListener {
            Toast.makeText(requireContext(), "Entrata registrata! ", Toast.LENGTH_SHORT).show()
            binding.tvTodayHours.text = "8h 00m" // Simulazione per ora
        }

        // Logica per il pulsante USCITA
        binding.btnCheckOut.setOnClickListener {
            Toast.makeText(requireContext(), "Uscita registrata! 👋", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
