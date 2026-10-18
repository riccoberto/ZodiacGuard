package com.zodiacguard.app.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.zodiacguard.app.data.WorkDataStore
import com.zodiacguard.app.data.WorkEntry
import com.zodiacguard.app.databinding.DialogNfcUnlockBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class NfcUnlockDialog(
    private val badgeUid: String,
    private val onMarkingComplete: () -> Unit
) : DialogFragment() {

    private var _binding: DialogNfcUnlockBinding? = null
    private val binding get() = _binding!!
    private lateinit var workDataStore: WorkDataStore
    private var isUnlocked = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogNfcUnlockBinding.inflate(inflater, container, false)
        workDataStore = WorkDataStore(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvBadgeInfo.text = "Badge: ${badgeUid.take(4)}...${badgeUid.takeLast(4)}"

        binding.sliderUnlock.addOnChangeListener(Slider.OnChangeListener { slider, value, fromUser ->
            if (fromUser && value >= 100f && !isUnlocked) {
                isUnlocked = true
                binding.layoutButtons.alpha = 1.0f
                binding.btnCheckIn.isEnabled = true
                binding.btnCheckOut.isEnabled = true
                binding.sliderUnlock.isEnabled = false
                Toast.makeText(requireContext(), "Sbloccato! Scegli ENTRATA o USCITA", Toast.LENGTH_SHORT).show()
            }
        })

        binding.btnCheckIn.setOnClickListener {
            registerMarking("ENTRATA")
        }

        binding.btnCheckOut.setOnClickListener {
            registerMarking("USCITA")
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun registerMarking(type: String) {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val minuteStr = String.format("%02d", minute)
        
        val entry = WorkEntry(
            timestamp = now.timeInMillis,
            type = type,
            activity = "Servizio",
            location = "Modena",
            date = WorkEntry.getCurrentDate()
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                workDataStore.addEntry(entry)
                Toast.makeText(
                    requireContext(),
                    "$type registrata alle $hour:$minuteStr",
                    Toast.LENGTH_LONG
                ).show()
                onMarkingComplete()
                dismiss()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
