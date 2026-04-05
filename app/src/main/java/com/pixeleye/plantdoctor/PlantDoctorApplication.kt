package com.pixeleye.plantdoctor

import android.app.Application
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel

class PlantDoctorApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Verbose Logging set for debugging
        OneSignal.Debug.logLevel = LogLevel.VERBOSE

        // Initialize OneSignal with a placeholder ID
        OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)
    }
}
