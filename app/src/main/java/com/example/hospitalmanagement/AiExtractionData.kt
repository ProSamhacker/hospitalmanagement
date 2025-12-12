package com.example.hospitalmanagement

import com.google.gson.annotations.SerializedName

// This matches the JSON we ask Gemini to generate
data class AiExtractionData(
    @SerializedName("symptoms") val symptoms: String,
    @SerializedName("diagnosis") val diagnosis: String,
    @SerializedName("medications") val medications: List<String>,
    @SerializedName("instructions") val instructions: String
)