package com.pixeleye.plantdoctor.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pixeleye.plantdoctor.MainActivity
import com.pixeleye.plantdoctor.R
import com.pixeleye.plantdoctor.data.local.AppDatabase
import com.pixeleye.plantdoctor.data.local.PlantReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("REMINDER_ID", -1)
        val plantName = intent.getStringExtra("PLANT_NAME") ?: "Plant"
        val careType = intent.getStringExtra("CARE_TYPE") ?: "Watering"
        
        if (reminderId == -1) return

        Log.d(TAG, "Reminder alarm triggered for: $plantName ($careType)")

        // Trigger notification
        showNotification(context, reminderId, plantName, careType)

        // Reschedule alarm for next day
        val db = AppDatabase.getDatabase(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val reminder = db.reminderDao().getReminderById(reminderId)
            if (reminder != null && reminder.isEnabled) {
                scheduleAlarm(context, reminder)
            }
        }
    }

    private fun showNotification(context: Context, id: Int, plantName: String, careType: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel for API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Plant Care Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications to remind you to water or fertilize your plants."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val title = "Plant Care Reminder 🌿"

        val body = when {
            careType.equals("Watering", ignoreCase = true) -> {
                "It's time to water your $plantName! 🌿"
            }
            careType.equals("Fertilizing", ignoreCase = true) -> {
                "It's time to fertilize your $plantName! 🌿"
            }
            else -> {
                "It's time to take care of your $plantName! 🌿"
            }
        }

        // Intent to open MainActivity when clicked
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }

    companion object {
        private const val CHANNEL_ID = "plant_care_channel"
        private const val TAG = "ReminderReceiver"

        fun scheduleAlarm(context: Context, reminder: PlantReminderEntity) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("REMINDER_ID", reminder.id)
                putExtra("PLANT_NAME", reminder.plantName)
                putExtra("CARE_TYPE", reminder.careType)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, reminder.hour)
                set(Calendar.MINUTE, reminder.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled alarm for reminder ${reminder.id} at ${reminder.hour}:${reminder.minute}")
        }

        fun cancelAlarm(context: Context, reminderId: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "Cancelled alarm for reminder $reminderId")
            }
        }
    }
}
