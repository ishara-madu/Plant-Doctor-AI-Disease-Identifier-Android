package com.pixeleye.plantdoctor.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM plant_reminders ORDER BY id DESC")
    fun getAllReminders(): Flow<List<PlantReminderEntity>>

    @Query("SELECT * FROM plant_reminders WHERE id = :id")
    suspend fun getReminderById(id: Int): PlantReminderEntity?

    @Query("SELECT * FROM plant_reminders")
    suspend fun getAllRemindersList(): List<PlantReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: PlantReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: PlantReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: PlantReminderEntity)

    @Query("SELECT * FROM plant_reminders WHERE scanId = :scanId")
    suspend fun getRemindersByScanId(scanId: String): List<PlantReminderEntity>

    @Query("DELETE FROM plant_reminders WHERE scanId = :scanId")
    suspend fun deleteRemindersByScanId(scanId: String)
}
