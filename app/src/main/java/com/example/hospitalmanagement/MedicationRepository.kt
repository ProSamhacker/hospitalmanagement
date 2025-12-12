package com.example.hospitalmanagement

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// UPDATED: Now accepts ConsultationDao to allow saving prescriptions
class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val consultationDao: ConsultationDao
) {

    // IMPORTANT: Replace with your actual Gemini API key.
    private val geminiApiKey = "AIzaSyC97B02tPs5C2JCvtTLE46fVhD9YFcaRSE"

    // --- Database Operations (Medications) ---

    suspend fun getAllMedications() = medicationDao.getAll()

    suspend fun insertMedication(medication: Medication) {
        medicationDao.insert(medication)
    }
    suspend fun updateMedication(medication: Medication) {
        medicationDao.update(medication)
    }
    suspend fun deleteMedication(medication: Medication) {
        medicationDao.delete(medication)
    }
    suspend fun deleteAllMedications() {
        medicationDao.deleteAll()
    }

    // --- NEW: Database Operations (Consultations & Prescriptions) ---

    /**
     * Saves the AI-extracted prescription data into the database.
     * This makes it visible in the Patient Dashboard.
     */
    suspend fun savePrescriptionFromAI(data: AiExtractionData) {
        // 1. In a real app, you would get the actual current Appointment ID.
        // For this prototype, we use a fixed ID (e.g., 1) so the patient can always find it.
        val dummyAppId = 1

        // 2. Loop through each medication identified by AI and save it
        for (medName in data.medications) {
            val prescription = Prescription(
                appId = dummyAppId,
                medicationName = medName,
                // We default dosage if not specifically extracted per-medication
                dosage = "See Instructions",
                instructions = data.instructions
            )
            consultationDao.savePrescription(prescription)
        }
    }

    // --- Gemini API Operations ---

    /**
     * The "Brain" Function: Extracts structured medical info from a voice transcript.
     */
    suspend fun extractMedicalInfo(transcript: String): AiExtractionData {
        // 1. Construct a smart prompt for Gemini
        // We strictly ask for JSON so we can parse it easily.
        val prompt = """
            Analyze this doctor-patient conversation transcript:
            "$transcript"
            
            Strictly return a valid JSON object with these exact keys. Do not use Markdown formatting (like ```json).
            {
                "symptoms": "List of symptoms detected (comma separated string)",
                "diagnosis": "Potential diagnosis based on symptoms",
                "medications": ["List of medicines mentioned"],
                "instructions": "Any dosage instructions mentioned"
            }
        """.trimIndent()

        // 2. Call Gemini
        val responseText = queryGemini(prompt, summarize = false)

        // 3. Parse the result
        return parseGeminiResponseToData(responseText)
    }

    /**
     * A general-purpose function to query the Gemini API.
     */
    suspend fun queryGemini(prompt: String, summarize: Boolean = true): String {
        val enhancedPrompt = if (summarize) {
            "Please provide a concise, brief answer (max 2-3 sentences) to: $prompt"
        } else {
            prompt
        }

        // Fixed URL (removed Markdown brackets from previous copy/paste)
        val url = "[https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash-lite:generateContent?key=$geminiApiKey](https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash-lite:generateContent?key=$geminiApiKey)"

        // Escape quotes in the prompt to avoid breaking the JSON request body
        val safePrompt = enhancedPrompt.replace("\"", "\\\"").replace("\n", " ")

        val requestBody = """
        {
          "contents": [{
            "parts":[{
              "text": "$safePrompt"
            }]
          }]
        }
        """.trimIndent()

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                Log.d("GEMINI_RESPONSE", "Raw JSON: $responseBody")
                extractTextFromGeminiResponse(responseBody)
            } catch (e: IOException) {
                "Network error: ${e.message}"
            }
        }
    }

    suspend fun correctMedicationSpelling(name: String): String {
        val prompt = "Correct this medication name: '$name'. Reply with ONLY the corrected name, nothing else."
        return queryGemini(prompt, summarize = false).trim()
    }

    /**
     * Helper: Extracts the raw text string from Gemini's complex JSON response.
     */
    private fun extractTextFromGeminiResponse(jsonString: String?): String {
        if (jsonString.isNullOrBlank()) return "Error: Empty response."

        return try {
            val gson = Gson()
            val responseMap = gson.fromJson(jsonString, Map::class.java)

            if (responseMap.containsKey("error")) {
                return "API Error"
            }

            val candidates = responseMap["candidates"] as? List<Map<String, Any>>
            if (candidates.isNullOrEmpty()) return "Error: No content."

            val content = candidates[0]["content"] as Map<String, Any>
            val parts = content["parts"] as List<Map<String, String>>
            val text = parts[0]["text"] ?: ""

            // Clean up any Markdown code blocks if Gemini ignores our "strict" instruction
            text.replace("```json", "").replace("```", "").trim()

        } catch (e: Exception) {
            "Error parsing response: ${e.localizedMessage}"
        }
    }

    /**
     * Helper: Converts the extracted text string (which is now JSON) into our Kotlin Data Object.
     */
    private fun parseGeminiResponseToData(jsonString: String): AiExtractionData {
        return try {
            val gson = Gson()
            val type = object : TypeToken<AiExtractionData>() {}.type
            gson.fromJson(jsonString, type)
        } catch (e: Exception) {
            Log.e("JSON_PARSE", "Failed to parse: $jsonString")
            // Return a fallback empty object if parsing fails
            AiExtractionData("Unknown", "Unknown", emptyList(), "Could not extract data")
        }
    }
    suspend fun getLaymanExplanation(query: String): String {
        val prompt = """
        You are a helpful medical assistant for a patient. 
        Explain the following medical term or question in very simple, plain English (max 2 sentences). 
        Avoid complex jargon.
        Question: "$query"
    """.trimIndent()

        // Re-use your existing queryGemini function
        return queryGemini(prompt, summarize = false)
    }
}