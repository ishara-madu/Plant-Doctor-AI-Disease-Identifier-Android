package com.pixeleye.plantdoctor.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pixeleye.plantdoctor.data.local.PlantReminderEntity
import com.pixeleye.plantdoctor.data.local.ReminderDao
import com.pixeleye.plantdoctor.receiver.ReminderReceiver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReminderViewModel(
    application: Application,
    private val reminderDao: ReminderDao
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    val reminders: StateFlow<List<PlantReminderEntity>> = reminderDao.getAllReminders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addReminders(
        scanId: String,
        plantName: String,
        wateringEnabled: Boolean,
        wateringHour: Int,
        wateringMinute: Int,
        fertilizingEnabled: Boolean,
        fertilizingHour: Int,
        fertilizingMinute: Int
    ) {
        val normalizedName = java.text.Normalizer.normalize(plantName.trim(), java.text.Normalizer.Form.NFC)
            .replace("\\s+".toRegex(), " ")
        viewModelScope.launch {
            if (wateringEnabled) {
                val entity = PlantReminderEntity(
                    scanId = scanId,
                    plantName = normalizedName,
                    careType = "Watering",
                    hour = wateringHour,
                    minute = wateringMinute,
                    isEnabled = true
                )
                val id = reminderDao.insertReminder(entity)
                ReminderReceiver.scheduleAlarm(context, entity.copy(id = id.toInt()))
            }
            if (fertilizingEnabled) {
                val entity = PlantReminderEntity(
                    scanId = scanId,
                    plantName = normalizedName,
                    careType = "Fertilizing",
                    hour = fertilizingHour,
                    minute = fertilizingMinute,
                    isEnabled = true
                )
                val id = reminderDao.insertReminder(entity)
                ReminderReceiver.scheduleAlarm(context, entity.copy(id = id.toInt()))
            }
        }
    }

    fun toggleReminder(reminder: PlantReminderEntity, isEnabled: Boolean) {
        viewModelScope.launch {
            val updated = reminder.copy(isEnabled = isEnabled)
            reminderDao.updateReminder(updated)
            if (isEnabled) {
                ReminderReceiver.scheduleAlarm(context, updated)
            } else {
                ReminderReceiver.cancelAlarm(context, updated.id)
            }
        }
    }

    fun updateReminderTime(reminder: PlantReminderEntity, hour: Int, minute: Int) {
        viewModelScope.launch {
            val updated = reminder.copy(hour = hour, minute = minute)
            reminderDao.updateReminder(updated)
            if (updated.isEnabled) {
                ReminderReceiver.scheduleAlarm(context, updated)
            }
        }
    }

    fun deleteReminder(reminder: PlantReminderEntity) {
        viewModelScope.launch {
            reminderDao.deleteReminder(reminder)
            ReminderReceiver.cancelAlarm(context, reminder.id)
        }
    }

    class Factory(
        private val application: Application,
        private val reminderDao: ReminderDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReminderViewModel(application, reminderDao) as T
        }
    }
}
