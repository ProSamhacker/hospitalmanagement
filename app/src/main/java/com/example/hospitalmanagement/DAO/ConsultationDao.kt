package com.example.hospitalmanagement.DAO

import androidx.room.*
import com.example.hospitalmanagement.AiExtraction
import com.example.hospitalmanagement.Appointment
import com.example.hospitalmanagement.ConsultationSession
import com.example.hospitalmanagement.Doctor
import com.example.hospitalmanagement.Patient
import com.example.hospitalmanagement.Prescription

@Dao
interface ConsultationDao {

    // --- 1. User Management ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoctor(doctor: Doctor)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: Patient)

    @Query("SELECT * FROM doctors")
    suspend fun getAllDoctors(): List<Doctor>

    // --- 2. Appointment Flow ---
    @Insert
    suspend fun createAppointment(appointment: Appointment): Long

    @Query("SELECT * FROM appointments WHERE doctorId = :doctorId AND status = 'Scheduled'")
    suspend fun getDoctorSchedule(doctorId: String): List<Appointment>

    // --- 3. The Voice Brain (Session & AI) ---
    @Insert
    suspend fun logSession(session: ConsultationSession): Long

    @Insert
    suspend fun saveExtraction(extraction: AiExtraction)

    // --- 4. The Outcome (Prescriptions) ---
    @Insert
    suspend fun savePrescription(prescription: Prescription)

    @Query("SELECT * FROM prescriptions WHERE appId = :appId")
    suspend fun getPrescriptionForAppt(appId: Int): Prescription?

    // Feature: Get a patient's full history
    @Transaction
    @Query("SELECT * FROM appointments WHERE patientId = :patientId")
    suspend fun getPatientHistory(patientId: String): List<Appointment>

    // Add this inside ConsultationDao interface
    @Query("SELECT * FROM prescriptions")
    suspend fun getAllPrescriptions(): List<Prescription>
}
