package com.example.hospitalmanagement

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech // Import TTS
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog // Import Dialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener { // Implement OnInitListener

    private var userRole: String = "PATIENT"
    private lateinit var repository: MedicationRepository
    private lateinit var tts: TextToSpeech // Declare TTS

    // UI Elements
    private var tvLiveTranscript: TextView? = null
    private val homeFragment = DoctorHomeFragment()
    private val featuresFragment = FeaturesFragment()
    private val patientHomeFragment = PatientHomeFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Init DB & Repo
        val database = AppDatabase.getDatabase(this)
        repository = MedicationRepository(database.medicationDao(), database.consultationDao())

        // 2. Init Text-To-Speech
        tts = TextToSpeech(this, this)

        // 3. Setup UI
        userRole = intent.getStringExtra("USER_ROLE") ?: "PATIENT"
        if (userRole == "DOCTOR") {
            setContentView(R.layout.activity_doctor_dashboard)
            setupDoctorUI()
        } else {
            setContentView(R.layout.activity_patient_dashboard)
            setupPatientUI()
        }
    }

    // --- SETUP UI (Same as before) ---
    private fun setupDoctorUI() {
        val fabMic = findViewById<FloatingActionButton>(R.id.fabMic)
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigationView)

        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, homeFragment).commit()

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    supportFragmentManager.beginTransaction().replace(R.id.fragment_container, homeFragment).commit()
                    true
                }
                R.id.nav_features -> {
                    supportFragmentManager.beginTransaction().replace(R.id.fragment_container, featuresFragment).commit()
                    true
                }
                else -> false
            }
        }
        fabMic.setOnClickListener { startVoiceRecognition() }
    }

    private fun setupPatientUI() {
        val fabMic = findViewById<FloatingActionButton>(R.id.fabMicPatient)
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigationViewPatient)

        supportFragmentManager.beginTransaction().replace(R.id.fragment_container_patient, patientHomeFragment).commit()

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    supportFragmentManager.beginTransaction().replace(R.id.fragment_container_patient, patientHomeFragment).commit()
                    true
                }
                R.id.nav_features -> {
                    supportFragmentManager.beginTransaction().replace(R.id.fragment_container_patient, featuresFragment).commit()
                    true
                }
                else -> false
            }
        }
        fabMic.setOnClickListener { startVoiceRecognition() }
    }

    // --- VOICE LOGIC ---
    private val speechLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val spokenText = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (!spokenText.isNullOrBlank()) {
                if (userRole == "DOCTOR") handleDoctorVoice(spokenText)
                else handlePatientVoice(spokenText)
            }
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
        }
        speechLauncher.launch(intent)
    }

    // --- DOCTOR LOGIC (Same as before) ---
    private fun handleDoctorVoice(command: String) {
        if (homeFragment.isVisible) homeFragment.updateTranscript("Processing: $command...")
        lifecycleScope.launch {
            try {
                val extractionResult = repository.extractMedicalInfo(command)
                repository.savePrescriptionFromAI(extractionResult)
                val displayResult = """
                    ✅ Saved to Database!
                    Rx: ${extractionResult.medications.joinToString(", ")}
                """.trimIndent()
                if (homeFragment.isVisible) homeFragment.updateTranscript(displayResult)
                Toast.makeText(this@MainActivity, "Prescription Saved!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                if (homeFragment.isVisible) homeFragment.updateTranscript("Error: ${e.localizedMessage}")
            }
        }
    }

    // --- PATIENT LOGIC (UPDATED: Layman Translator) ---
    private fun handlePatientVoice(command: String) {
        // Check if the user is asking a question (Who, What, Explain, Mean, etc.)
        val lowerCmd = command.lowercase()
        if (lowerCmd.contains("what") || lowerCmd.contains("mean") || lowerCmd.contains("explain") || lowerCmd.contains("define")) {

            Toast.makeText(this, "Translating...", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                try {
                    // 1. Get simple explanation from Gemini
                    val explanation = repository.getLaymanExplanation(command)

                    // 2. Speak it out (Voice First!)
                    speakOut(explanation)

                    // 3. Show it in a Dialog so they can read it too
                    showExplanationDialog(command, explanation)

                } catch (e: Exception) {
                    speakOut("Sorry, I couldn't translate that.")
                }
            }
        } else {
            // Default Fallback
            speakOut("I heard: $command")
        }
    }

    // Helper: TTS Speak
    private fun speakOut(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // Helper: Show Dialog
    private fun showExplanationDialog(query: String, answer: String) {
        AlertDialog.Builder(this)
            .setTitle("💡 Layman Translator")
            .setMessage("Q: $query\n\n$answer")
            .setPositiveButton("Got it", null)
            .show()
    }

    // TTS Init
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}