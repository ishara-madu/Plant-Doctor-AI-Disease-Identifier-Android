package com.pixeleye.plantdoctor.data.api

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pixeleye.plantdoctor.data.local.HistoryDao
import com.pixeleye.plantdoctor.data.local.ReminderDao
import com.pixeleye.plantdoctor.data.local.toEntity
import com.pixeleye.plantdoctor.receiver.ReminderReceiver
import android.content.Context

class PlantScanRepository(
    private val supabaseClient: SupabaseClient,
    private val historyDao: HistoryDao,
    private val reminderDao: ReminderDao,
    private val context: Context
) {

    companion object {
        private const val TAG = "PlantScanRepo"
        private const val TABLE_NAME = "plant_scans"
        private const val STORAGE_BUCKET = "plant-images"
    }

    /**
     * Returns a Flow of the cached history directly from Room.
     */
    fun getHistoryFlow(): Flow<List<PlantScanDto>> {
        return historyDao.getAllHistory().map { list ->
            list.map { it.toDto() }
        }
    }

    /**
     * Fetches the latest history from Supabase and overwrites the local cache.
     * Enforces the 10 item size limit locally.
     */
    suspend fun refreshHistory() {
        try {
            val results = supabaseClient
                .from(TABLE_NAME)
                .select()
                .decodeList<PlantScanDto>()
                .sortedByDescending { it.createdAt }
            Log.d(TAG, "Fetched ${results.size} scans from remote")
            
            // Insert into local DB
            historyDao.insertAllAndEnforceLimit(results.map { it.toEntity() })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh history from remote", e)
            throw e
        }
    }

    /**
     * Inserts a new scan to Supabase, then caches it locally.
     */
    suspend fun insertScan(scan: PlantScanDto): PlantScanDto {
        return try {
            val inserted = supabaseClient
                .from(TABLE_NAME)
                .insert(scan) {
                    select()
                }
                .decodeSingleOrNull<PlantScanDto>() ?: scan
            
            historyDao.insertHistoryAndEnforceLimit(inserted.toEntity())
            inserted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert new scan to remote", e)
            throw e
        }
    }

    suspend fun insertScanLocal(scan: PlantScanDto): PlantScanDto {
        historyDao.insertHistoryAndEnforceLimit(scan.toEntity())
        return scan
    }

    private suspend fun deleteSingleScanInternal(scan: PlantScanDto) {
        val scanId = scan.id ?: return
        val filePath = extractStorageFilePath(scan.imageUrl)
        if (filePath != null) {
            try {
                supabaseClient.storage.from(STORAGE_BUCKET).delete(listOf(filePath))
                Log.d("DELETE_ACTION", "Deleted storage file: $filePath")
            } catch (e: Exception) {
                Log.w("DELETE_ACTION", "Storage delete failed for $filePath, proceeding with DB delete", e)
            }
        }

        supabaseClient.from(TABLE_NAME).delete {
            filter {
                eq("id", scanId)
            }
        }
        historyDao.deleteHistoryById(scanId)
        Log.d(TAG, "Deleted DB record locally: $scanId")
    }

    suspend fun deleteScan(scan: PlantScanDto) {
        try {
            // If it's a root scan, delete all follow-ups first!
            if (scan.parentId.isNullOrBlank()) {
                val scanId = scan.id
                if (scanId != null) {
                    val followUps = historyDao.getFollowUps(scanId)
                    followUps.forEach { followUp ->
                        try {
                            deleteSingleScanInternal(followUp.toDto())
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to delete follow-up scan: ${followUp.id}", e)
                        }
                    }

                    // Automatically delete associated care reminders / notifications
                    try {
                        val reminders = reminderDao.getRemindersByScanId(scanId)
                        reminders.forEach { reminder ->
                            ReminderReceiver.cancelAlarm(context, reminder.id)
                        }
                        reminderDao.deleteRemindersByScanId(scanId)
                        Log.d(TAG, "Deleted reminders associated with root scan: $scanId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete reminders for scan: $scanId", e)
                    }
                }
            }
            // Finally delete the scan itself
            deleteSingleScanInternal(scan)
        } catch (e: Exception) {
            Log.e("DELETE_ACTION", "Supabase delete failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun getThreadScansLocal(parentId: String): List<PlantScanDto> {
        return historyDao.getThreadScans(parentId).map { it.toDto() }
    }

    suspend fun getThreadScansRemote(parentId: String): List<PlantScanDto> {
        return try {
            val allScans = supabaseClient
                .from(TABLE_NAME)
                .select()
                .decodeList<PlantScanDto>()
            allScans
                .filter { it.id == parentId || it.parentId == parentId }
                .sortedBy { it.createdAt }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get thread scans from remote for parentId: $parentId", e)
            emptyList()
        }
    }

    suspend fun updatePlantNameLocal(id: String, plantName: String) {
        historyDao.updatePlantName(id, plantName)
    }

    suspend fun getHistoryByIdLocal(id: String): PlantScanDto? {
        return historyDao.getHistoryById(id)?.toDto()
    }

    suspend fun syncPlantNamesFromReminders() = withContext(Dispatchers.IO) {
        try {
            val reminders = reminderDao.getAllRemindersList()
            reminders.forEach { reminder ->
                if (reminder.plantName.isNotBlank()) {
                    val normalizedName = java.text.Normalizer.normalize(reminder.plantName.trim(), java.text.Normalizer.Form.NFC)
                        .replace("\\s+".toRegex(), " ")
                    historyDao.updatePlantName(reminder.scanId, normalizedName)
                }
            }
            Log.d(TAG, "Synced plant names from reminders to history table")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync plant names from reminders", e)
        }
    }

    /**
     * Extracts the storage object path from a Supabase public URL.
     *
     * Supabase public URLs follow the pattern:
     *   https://<project-ref>.supabase.co/storage/v1/object/public/<bucket>/<file-path>
     *
     * This function strips the bucket prefix and returns only the file path
     * relative to the bucket (e.g. "abc123.jpg" or "folder/abc123.jpg").
     *
     * Returns null if the URL cannot be parsed.
     */
    private fun extractStorageFilePath(url: String): String? {
        return try {
            val marker = "/storage/v1/object/public/$STORAGE_BUCKET/"
            val index = url.indexOf(marker)
            if (index == -1) {
                Log.w(TAG, "URL does not match expected Supabase storage pattern: $url")
                return null
            }
            url.substring(index + marker.length)
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract storage path from URL: $url", e)
            null
        }
    }
}
