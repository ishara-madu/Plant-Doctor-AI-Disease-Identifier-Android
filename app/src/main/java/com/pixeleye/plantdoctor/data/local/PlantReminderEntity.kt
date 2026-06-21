package com.pixeleye.plantdoctor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plant_reminders")
data class PlantReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val scanId: String,
    val plantName: String,
    val careType: String, // "Watering" or "Fertilizing"
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true
)
