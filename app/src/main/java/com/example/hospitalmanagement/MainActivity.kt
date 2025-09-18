package com.example.hospitalmanagement

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var resultText: TextView
    private lateinit var speakBtn: Button
    private lateinit var adapter: MedicationAdapter
    private lateinit var medList: RecyclerView
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Adjust padding for system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Init views
        resultText = findViewById(R.id.resultText)
        speakBtn = findViewById(R.id.speakBtn)
        medList = findViewById(R.id.medList)

        // Setup RecyclerView
        adapter = MedicationAdapter(emptyList())
        medList.layoutManager = LinearLayoutManager(this)
        medList.adapter = adapter
        medList.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        // Init DB + TTS
        db = AppDatabase.getDatabase(this)
        tts = TextToSpeech(this, this)

        // Setup Speech Recognition
        val speechLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val spokenText =
                    result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
                spokenText?.let { handleCommand(it) }
            }
        }

        speakBtn.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            speechLauncher.launch(intent)
        }

        // Load existing medications
        loadMedications()
    }

    private fun loadMedications() {
        lifecycleScope.launch {
            val meds = db.medicationDao().getAll()
            runOnUiThread { adapter.updateData(meds) }
        }
    }

    private fun handleCommand(command: String) {
        resultText.text = command

        lifecycleScope.launch {
            val dao = db.medicationDao()
            val response: String

            when {
                command.contains("add", ignoreCase = true) -> {
                    val section = extractSection(command)
                    val medName = extractMedicationName(command)
                    dao.insert(Medication(name = medName, section = section))
                    response = "Medication '$medName' added to $section."
                }
                command.contains("delete", ignoreCase = true) -> {
                    val section = extractSection(command)
                    val med = dao.findBySection(section)
                    response = if (med != null) {
                        dao.delete(med)
                        "Medication '${med.name}' deleted from $section."
                    } else "No medication found in $section."
                }
                command.contains("update", ignoreCase = true) -> {
                    val section = extractSection(command)
                    val med = dao.findBySection(section)
                    response = if (med != null) {
                        val updatedName = extractMedicationName(command).ifEmpty { "Updated Medication" }
                        val updated = med.copy(name = updatedName)
                        dao.update(updated)
                        "Medication updated to '$updatedName' in $section."
                    } else "No medication found to update in $section."
                }
                else -> {
                    response = "Sorry, I didn’t understand that command."
                }
            }

            val allMeds = dao.getAll()

            runOnUiThread {
                resultText.text = "You said: $command\n$response"
                adapter.updateData(allMeds)
                speakOut(response)
            }
        }
    }

    // Extracts section after "to", defaults to "General"
    private fun extractSection(command: String): String {
        return command.substringAfterLast("to", "General").trim()
    }

    // Extracts medication name between "add"/"update" and "to"
    private fun extractMedicationName(command: String): String {
        return command.substringAfter("add", "")
            .substringAfter("update", "")
            .substringBefore("to")
            .trim()
            .ifEmpty { "Medication" }
    }

    private fun speakOut(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

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
