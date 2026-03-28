package com.pixeleye.plantdoctor.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Context.scanDataStore: DataStore<Preferences> by preferencesDataStore(name = "scan_quota")

class ScanQuotaManager(private val context: Context) {

    private object Keys {
        val LAST_SCAN_DATE = stringPreferencesKey("last_scan_date")
        val DAILY_SCAN_COUNT = intPreferencesKey("daily_scan_count")
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    val todayScanCount: Flow<Int> = context.scanDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val storedDate = preferences[Keys.LAST_SCAN_DATE] ?: ""
            val today = LocalDate.now().format(dateFormatter)
            if (storedDate == today) {
                preferences[Keys.DAILY_SCAN_COUNT] ?: 0
            } else {
                0
            }
        }

    suspend fun incrementScanCount() {
        val today = LocalDate.now().format(dateFormatter)
        context.scanDataStore.edit { preferences ->
            val storedDate = preferences[Keys.LAST_SCAN_DATE] ?: ""
            if (storedDate != today) {
                preferences[Keys.LAST_SCAN_DATE] = today
                preferences[Keys.DAILY_SCAN_COUNT] = 1
            } else {
                val currentCount = preferences[Keys.DAILY_SCAN_COUNT] ?: 0
                preferences[Keys.DAILY_SCAN_COUNT] = currentCount + 1
            }
        }
    }

    suspend fun grantExtraScan(currentCount: Int) {
        val today = LocalDate.now().format(dateFormatter)
        context.scanDataStore.edit { preferences ->
            preferences[Keys.LAST_SCAN_DATE] = today
            preferences[Keys.DAILY_SCAN_COUNT] = currentCount - 1
        }
    }
}
