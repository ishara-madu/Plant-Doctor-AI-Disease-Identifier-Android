package com.pixeleye.plantdoctor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pixeleye.plantdoctor.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device booted, rescheduling active reminders...")
            
            val db = AppDatabase.getDatabase(context)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            
            scope.launch {
                val reminders = db.reminderDao().getAllReminders().firstOrNull() ?: emptyList()
                val activeReminders = reminders.filter { it.isEnabled }
                
                activeReminders.forEach { reminder ->
                    ReminderReceiver.scheduleAlarm(context, reminder)
                }
                Log.d(TAG, "Rescheduled ${activeReminders.size} active reminders successfully.")
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
