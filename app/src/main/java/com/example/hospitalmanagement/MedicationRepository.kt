package com.example.hospitalmanagement

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MedicationRepository(private val medicationDao: MedicationDao) {

    // IMPORTANT: Replace with your actual Gemini API key.
    // For production, load this from a secure place, not hardcoded.
    private val geminiApiKey = "AIzaSyC97B02tPs5C2JCvtTLE46fVhD9YFcaRSE"

    // --- Database Operations ---

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

    // --- Gemini API Operations ---

    /**
     * A general-purpose function to query the Gemini API.
     * @param prompt The question or command for the AI.
     * @param summarize If true, asks Gemini to provide a concise summary
     * @return A String containing the AI's response or an error message.
     */
    suspend fun queryGemini(prompt: String, summarize: Boolean = true): String {
        val enhancedPrompt = if (summarize) {
            "Please provide a concise, brief answer (max 2-3 sentences) to: $prompt"
        } else {
            prompt
        }

        val url = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash-lite:generateContent?key=$geminiApiKey"
        val requestBody = """
        {
          "contents": [{
            "parts":[{
              "text": "$enhancedPrompt"
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

                // Diagnostic Log: Prints the raw server response to Logcat
                Log.d("GEMINI_RESPONSE", "Raw JSON: $responseBody")

                val fullResponse = extractTextFromGeminiResponse(responseBody)

                // Additional truncation for very long responses
                if (fullResponse.length > 300) {
                    fullResponse.take(297) + "..."
                } else {
                    fullResponse
                }
            } catch (e: IOException) {
                "Network error: ${e.message}"
            }
        }
    }

    /**
     * Asks Gemini to correct the spelling of a medication name.
     */
    suspend fun correctMedicationSpelling(name: String): String {
        val prompt = "Correct this medication name: '$name'. Reply with ONLY the corrected name, nothing else."
        return queryGemini(prompt, summarize = false).trim()
    }

    /**
     * Parses the JSON response from Gemini, handling both success and error cases.
     * @param jsonString The raw JSON string from the API response.
     * @return The extracted text or a formatted error message.
     */
    private fun extractTextFromGeminiResponse(jsonString: String?): String {
        if (jsonString.isNullOrBlank()) {
            return "Error: Empty response from server."
        }

        return try {
            val gson = Gson()
            // Use a generic Map to check the structure
            val responseMap = gson.fromJson(jsonString, Map::class.java)

            // ** Check for an error field first **
            if (responseMap.containsKey("error")) {
                val error = responseMap["error"] as Map<String, Any>
                val message = error["message"] as String
                return "API Error: $message"
            }

            // ** Check for a safety block (promptFeedback) **
            val candidates = responseMap["candidates"] as? List<Map<String, Any>>
            if (candidates.isNullOrEmpty()) {
                val promptFeedback = responseMap["promptFeedback"] as? Map<String, Any>
                if (promptFeedback != null) {
                    val blockReason = promptFeedback["blockReason"] as? String
                    return "Blocked by safety filter: $blockReason"
                }
                return "Error: No valid content in response."
            }

            // If no error, parse the successful response
            val content = candidates[0]["content"] as Map<String, Any>
            val parts = content["parts"] as List<Map<String, String>>
            parts[0]["text"] ?: "No response text found."

        } catch (e: Exception) {
            // This will now catch other unexpected issues
            "Error parsing response: ${e.localizedMessage}"
        }
    }
}