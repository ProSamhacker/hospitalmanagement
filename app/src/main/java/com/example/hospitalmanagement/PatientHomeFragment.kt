package com.example.hospitalmanagement

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.util.Locale

class PatientHomeFragment : Fragment(), TextToSpeech.OnInitListener {

    private lateinit var adapter: PrescriptionAdapter
    private lateinit var tts: TextToSpeech
    private lateinit var database: AppDatabase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_patient_home, container, false)

        // 1. Initialize Database & Text-to-Speech
        database = AppDatabase.getDatabase(requireContext())
        tts = TextToSpeech(requireContext(), this)

        // 2. Setup RecyclerView
        val rv = view.findViewById<RecyclerView>(R.id.rvPrescriptions)
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = PrescriptionAdapter(emptyList(), tts)
        rv.adapter = adapter

        // 3. Load Data from Database
        loadPrescriptions()

        return view
    }

    // Function to fetch data from Room DB
    fun loadPrescriptions() {
        lifecycleScope.launch {
            // Fetch all prescriptions for the dummy patient (ID 1)
            // Note: Ensure your ConsultationDao has the 'getAllPrescriptions()' function
            val allPrescriptions = database.consultationDao().getAllPrescriptions()
            adapter.updateData(allPrescriptions)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        }
    }

    // Helper to refresh the list when switching tabs
    override fun onResume() {
        super.onResume()
        loadPrescriptions()
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}