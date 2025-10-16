package com.example.hospitalmanagement

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var resultText: TextView
    private lateinit var speakBtn: FloatingActionButton
    private lateinit var stopBtn: FloatingActionButton
    private lateinit var addManuallyBtn: FloatingActionButton
    private lateinit var adapter: MedicationAdapter
    private lateinit var medList: RecyclerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        resultText = findViewById(R.id.resultText)
        speakBtn = findViewById(R.id.speakBtn)
        stopBtn = findViewById(R.id.stopBtn)
        addManuallyBtn = findViewById(R.id.addManuallyBtn)
        medList = findViewById(R.id.medList)
        loadingIndicator = findViewById(R.id.loadingIndicator)

        // Setup RecyclerView
        adapter = MedicationAdapter(emptyList())
        medList.layoutManager = LinearLayoutManager(this)
        medList.adapter = adapter

        // Setup ViewModel
        val database = AppDatabase.getDatabase(this)
        val repository = MedicationRepository(database.medicationDao())
        val viewModelFactory = MainViewModel.Factory(repository)
        viewModel = ViewModelProvider(this, viewModelFactory).get(MainViewModel::class.java)

        tts = TextToSpeech(this, this)

        // Setup Observers & Listeners
        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewModel.medications.observe(this) { meds ->
            adapter.updateData(meds)
        }

        viewModel.responseMessage.observe(this) { response ->
            resultText.text = response
            speakOut(response)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            val isEnabled = !isLoading
            speakBtn.isEnabled = isEnabled
            addManuallyBtn.isEnabled = isEnabled
            speakBtn.alpha = if (isLoading) 0.5f else 1.0f
            addManuallyBtn.alpha = if (isLoading) 0.5f else 1.0f
        }
    }

    private fun setupClickListeners() {
        val speechLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val spokenText =
                    result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
                if (!spokenText.isNullOrBlank()) {
                    resultText.text = "You said: $spokenText"
                    viewModel.processVoiceCommand(spokenText)
                }
            }
        }

        speakBtn.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            speechLauncher.launch(intent)
        }

        stopBtn.setOnClickListener {
            if (::tts.isInitialized) {
                tts.stop()
            }
        }

        addManuallyBtn.setOnClickListener {
            showAddMedicationDialog()
        }
    }

    private fun showAddMedicationDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_medication, null)
        val etMedName = dialogView.findViewById<EditText>(R.id.etMedicationName)
        val etMedSection = dialogView.findViewById<EditText>(R.id.etMedicationSection)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Add") { dialog, _ ->
                val name = etMedName.text.toString().trim()
                val section = etMedSection.text.toString().trim()

                if (name.isNotEmpty()) {
                    viewModel.addMedicationManually(name, section)
                } else {
                    Toast.makeText(this, "Medication name cannot be empty", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .create()
            .show()
    }

    private fun speakOut(text: String) {
        val utteranceId = this.hashCode().toString()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread { stopBtn.visibility = View.VISIBLE }
                }
                override fun onDone(utteranceId: String?) {
                    runOnUiThread { stopBtn.visibility = View.GONE }
                }
                override fun onError(utteranceId: String?) {
                    runOnUiThread { stopBtn.visibility = View.GONE }
                }
            })
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