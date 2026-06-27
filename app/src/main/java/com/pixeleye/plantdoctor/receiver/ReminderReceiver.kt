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
        val customMessage = intent.getStringExtra("CUSTOM_MESSAGE")
        
        if (reminderId == -1) return

        Log.d(TAG, "Reminder alarm triggered for: $plantName ($careType)")

        // Trigger notification
        showNotification(context, reminderId, plantName, careType, customMessage)

        // Reschedule alarm for next day (only if not a one-shot follow-up alarm)
        if (!careType.equals("FollowUp", ignoreCase = true)) {
            val db = AppDatabase.getDatabase(context)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope.launch {
                val reminder = db.reminderDao().getReminderById(reminderId)
                if (reminder != null && reminder.isEnabled) {
                    scheduleAlarm(context, reminder)
                }
            }
        }
    }

    private fun showNotification(context: Context, id: Int, plantName: String, careType: String, customMessage: String? = null) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel for API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = context.getString(R.string.notification_channel_name)
            val channelDesc = context.getString(R.string.notification_channel_desc)
            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = channelDesc
            }
            notificationManager.createNotificationChannel(channel)
        }

        val title = if (careType.equals("FollowUp", ignoreCase = true)) {
            context.getString(R.string.notification_follow_up_title)
        } else {
            context.getString(R.string.notification_title)
        }

        val body = when {
            careType.equals("FollowUp", ignoreCase = true) -> {
                customMessage ?: context.getString(R.string.notification_body_default, plantName)
            }
            careType.equals("Watering", ignoreCase = true) -> {
                context.getString(R.string.notification_body_watering, plantName)
            }
            careType.equals("Fertilizing", ignoreCase = true) -> {
                context.getString(R.string.notification_body_fertilizing, plantName)
            }
            else -> {
                context.getString(R.string.notification_body_default, plantName)
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

        fun scheduleFollowUpAlarm(context: Context, rootScanId: String, plantName: String, message: String, daysToWait: Int = 3) {
            if (rootScanId.isBlank() || message.isBlank()) return
            val requestCode = rootScanId.hashCode()
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("REMINDER_ID", requestCode)
                putExtra("PLANT_NAME", plantName)
                putExtra("CARE_TYPE", "FollowUp")
                putExtra("CUSTOM_MESSAGE", message)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, daysToWait)
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
            Log.d(TAG, "Scheduled AI follow-up alarm for $plantName in $daysToWait days at ${calendar.time} with request code $requestCode")
        }

        fun cancelFollowUpAlarm(context: Context, rootScanId: String) {
            if (rootScanId.isBlank()) return
            val requestCode = rootScanId.hashCode()
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "Cancelled follow-up alarm for plant $rootScanId (requestCode $requestCode)")
            }
        }
    }
}
