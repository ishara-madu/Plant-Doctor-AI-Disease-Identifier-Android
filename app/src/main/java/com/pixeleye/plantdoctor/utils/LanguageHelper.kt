package com.pixeleye.plantdoctor.utils

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.util.Log
import java.util.Locale

object LanguageHelper {
    private const val TAG = "LanguageHelper"

    fun getLanguageTag(language: String): String {
        return when (language.lowercase()) {
            "sinhala" -> "si"
            "tamil" -> "ta"
            "spanish" -> "es"
            "hindi" -> "hi"
            "french" -> "fr"
            "portuguese" -> "pt"
            "japanese" -> "ja"
            else -> "en"
        }
    }

    fun setAppLocale(context: Context, language: String) {
        val tag = getLanguageTag(language)
        Log.d(TAG, "Setting app locale to: $tag ($language)")
        
        try {
            // Android 13+ (API 33+) native app-specific language settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as? LocaleManager
                if (localeManager != null) {
                    val currentLocales = localeManager.applicationLocales
                    if (currentLocales.size() > 0 && currentLocales.get(0).language == tag) {
                        Log.d(TAG, "App locale on API 33+ is already set to: $tag. Skipping update.")
                        return
                    }
                    localeManager.applicationLocales = LocaleList.forLanguageTags(tag)
                    return
                }
            }
            
            // Legacy / Fallback for API < 33
            val resources = context.resources
            val config = resources.configuration
            val currentLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (config.locales.size() > 0) config.locales.get(0) else null
            } else {
                @Suppress("DEPRECATION")
                config.locale
            }

            if (currentLocale?.language == tag) {
                Log.d(TAG, "App locale on legacy API is already set to: $tag. Skipping update.")
                return
            }

            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(locale))
            } else {
                @Suppress("DEPRECATION")
                config.locale = locale
            }
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
            
            // Recreate activity to apply configuration changes immediately
            (context as? Activity)?.recreate()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set app locale", e)
        }
    }
}
