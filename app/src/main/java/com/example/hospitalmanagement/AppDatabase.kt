package com.example.hospitalmanagement

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Medication::class, // Your existing code
        Doctor::class,
        Patient::class,
        Appointment::class,
        ConsultationSession::class,
        AiExtraction::class,
        Prescription::class
    ],
    version = 2 // IMPORTANT: Increment version number
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun consultationDao(): ConsultationDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hospital_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
