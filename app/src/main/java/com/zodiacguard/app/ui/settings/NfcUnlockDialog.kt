package com.zodiacguard.app.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.zodiacguard.app.R
import com.zodiacguard.app.data.WorkDataStore
import com.zodiacguard.app.data.WorkEntry
import kotlinx.coroutines.launch
import java.util.Calendar

class NfcUnlockDialog(
    private val badgeUid: String,
    private val onMarkingComplete: () -> Unit
) : DialogFragment() {

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
    ): View? {
        workDataStore = WorkDataStore(requireContext())
        return inflater.inflate(R.layout.dialog_nfc_unlock, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvBadgeInfo = view.findViewById<TextView>(R.id.tvBadgeInfo)
        val sliderUnlock = view.findViewById<Slider>(R.id.sliderUnlock)
        val layoutDetails = view.findViewById<LinearLayout>(R.id.layoutDetails)
        val layoutButtons = view.findViewById<LinearLayout>(R.id.layoutButtons)
        val btnCheckIn = view.findViewById<MaterialButton>(R.id.btnCheckIn)
        val btnCheckOut = view.findViewById<MaterialButton>(R.id.btnCheckOut)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)
        val etActivity = view.findViewById<TextInputEditText>(R.id.etActivity)
        val etLocation = view.findViewById<TextInputEditText>(R.id.etLocation)
        val switchProduction = view.findViewById<SwitchMaterial>(R.id.switchProduction)

        tvBadgeInfo.text = "Badge: ${badgeUid.take(4)}...${badgeUid.takeLast(4)}"

        // Inizialmente disabilita tutto
        btnCheckIn.isEnabled = false
        btnCheckOut.isEnabled = false

        // Slider per sbloccare
        sliderUnlock.addOnChangeListener(Slider.OnChangeListener { slider, value, fromUser ->
            if (fromUser && value >= 100f && !isUnlocked) {
                isUnlocked = true
                // Abilita la sezione dettagli
                layoutDetails.alpha = 1.0f
                layoutDetails.isClickable = true
                layoutDetails.isFocusable = true
                // Abilita i pulsanti
                layoutButtons.alpha = 1.0f
                layoutButtons.isClickable = true
                layoutButtons.isFocusable = true
                btnCheckIn.isEnabled = true
                btnCheckOut.isEnabled = true
                sliderUnlock.isEnabled = false
                Toast.makeText(requireContext(), "Sbloccato! Compila i dettagli e scegli ENTRATA o USCITA", Toast.LENGTH_SHORT).show()
            }
        })

        btnCheckIn.setOnClickListener {
            val activity = etActivity.text.toString().ifEmpty { "Servizio" }
            val location = etLocation.text.toString().ifEmpty { "Modena" }
            val isProduction = switchProduction.isChecked
            registerMarking("ENTRATA", activity, location, isProduction)
        }

        btnCheckOut.setOnClickListener {
            val activity = etActivity.text.toString().ifEmpty { "Servizio" }
            val location = etLocation.text.toString().ifEmpty { "Modena" }
            val isProduction = switchProduction.isChecked
            registerMarking("USCITA", activity, location, isProduction)
        }

        btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun registerMarking(type: String, activity: String, location: String, isProduction: Boolean) {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val minuteStr = String.format("%02d", minute)
        val currentDate = WorkEntry.getCurrentDate()
        
        val entry = WorkEntry(
            timestamp = now.timeInMillis,
            type = type,
            activity = activity,
            location = location,
            date = currentDate
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                workDataStore.addEntry(entry)
                
                // Se è modalità produzione, salva il giorno
                if (isProduction) {
                    workDataStore.saveProductionMode(currentDate, true)
                }
                
                Toast.makeText(
                    requireContext(),
                    "$type registrata alle $hour:$minuteStr\nAttività: $activity\nLuogo: $location",
                    Toast.LENGTH_LONG
                ).show()
                onMarkingComplete()
                dismiss()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
