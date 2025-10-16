package com.example.hospitalmanagement
import androidx.room.*

@Dao
interface MedicationDao {
    @Insert
    suspend fun insert(medication: Medication)

    @Update
    suspend fun update(medication: Medication)

    @Delete
    suspend fun delete(medication: Medication)

    @Query("SELECT * FROM medications")
    suspend fun getAll(): List<Medication>

    @Query("DELETE FROM medications")
    suspend fun deleteAll()

    @Query("SELECT * FROM medications WHERE section = :section LIMIT 1")
    suspend fun findBySection(section: String): Medication?
}
