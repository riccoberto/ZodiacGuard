package com.zodiacguard.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.zodiacguard.app.R
import com.zodiacguard.app.databinding.FragmentHomeBinding
import com.zodiacguard.app.ui.navigation.navigateWithTransition

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnDashboard.setOnClickListener {
            findNavController().navigateWithTransition(R.id.dashboardFragment)
        }

        binding.btnWeekly.setOnClickListener {
            findNavController().navigateWithTransition(R.id.weeklyFragment)
        }

        binding.btnMonthly.setOnClickListener {
            findNavController().navigateWithTransition(R.id.monthlyFragment)
        }

        binding.btnCounters.setOnClickListener {
            findNavController().navigateWithTransition(R.id.manualCountersFragment)
        }

        binding.btnSettings.setOnClickListener {
            findNavController().navigateWithTransition(R.id.settingsFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
