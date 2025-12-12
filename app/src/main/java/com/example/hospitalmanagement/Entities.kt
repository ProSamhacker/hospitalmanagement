package com.example.hospitalmanagement

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

// 1. The Doctor (User Role A)
@Entity(tableName = "doctors")
data class Doctor(
    @PrimaryKey val doctorId: String, // e.g., "DOC-001"
    val name: String,
    val specialization: String, // e.g., "Cardiologist"
    val phone: String
)

// 2. The Patient (User Role B)
@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey val patientId: String, // e.g., "PAT-2024-99"
    val name: String,
    val age: Int,
    val gender: String,
    val phone: String
)

// 3. The Appointment (The Hub)
@Entity(
    tableName = "appointments",
    foreignKeys = [
        ForeignKey(entity = Doctor::class, parentColumns = ["doctorId"], childColumns = ["doctorId"]),
        ForeignKey(entity = Patient::class, parentColumns = ["patientId"], childColumns = ["patientId"])
    ]
)
data class Appointment(
    @PrimaryKey(autoGenerate = true) val appId: Int = 0,
    val doctorId: String,
    val patientId: String,
    val dateTime: Long, // Store as timestamp
    val status: String // "Scheduled", "Completed", "Cancelled"
)

// 4. The Voice Session (The Brain)
@Entity(
    tableName = "consultation_sessions",
    foreignKeys = [ForeignKey(entity = Appointment::class, parentColumns = ["appId"], childColumns = ["appId"])]
)
data class ConsultationSession(
    @PrimaryKey(autoGenerate = true) val sessionId: Int = 0,
    val appId: Int,
    val audioFilePath: String, // Path to the recording on phone storage
    val fullTranscript: String // What Gemini heard
)

// 5. The Intelligent Extraction (What Gemini Understood)
@Entity(
    tableName = "ai_extractions",
    foreignKeys = [ForeignKey(entity = ConsultationSession::class, parentColumns = ["sessionId"], childColumns = ["sessionId"])]
)
data class AiExtraction(
    @PrimaryKey(autoGenerate = true) val extractId: Int = 0,
    val sessionId: Int,
    val detectedSymptoms: String, // e.g., "Fever, Headache"
    val suggestedDiagnosis: String
)

// 6. The Prescription (The Outcome)
// This links back to your existing Medication table logic!
@Entity(
    tableName = "prescriptions",
    foreignKeys = [ForeignKey(entity = Appointment::class, parentColumns = ["appId"], childColumns = ["appId"])]
)
data class Prescription(
    @PrimaryKey(autoGenerate = true) val scriptId: Int = 0,
    val appId: Int,
    val medicationName: String, // Validated against your Medication Table
    val dosage: String,         // e.g., "500mg"
    val instructions: String    // e.g., "Twice a day after food"
)