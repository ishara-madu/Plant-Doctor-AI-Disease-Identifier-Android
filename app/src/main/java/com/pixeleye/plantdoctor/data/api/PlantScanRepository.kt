package com.pixeleye.plantdoctor.data.api

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.pixeleye.plantdoctor.data.local.HistoryDao
import com.pixeleye.plantdoctor.data.local.toEntity

class PlantScanRepository(
    private val supabaseClient: SupabaseClient,
    private val historyDao: HistoryDao
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
            historyDao.insertAll(results.map { it.toEntity() })
            historyDao.enforceSizeLimit()
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
                .insert(scan)
                .decodeSingleOrNull<PlantScanDto>() ?: scan
            
            historyDao.insertHistory(inserted.toEntity())
            historyDao.enforceSizeLimit()
            inserted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert new scan to remote", e)
            throw e
        }
    }

    suspend fun deleteScan(scan: PlantScanDto) {
        val scanId = scan.id ?: return

        // Step 1: Delete the image from Supabase Storage FIRST.
        // If this fails (e.g. file not found / already deleted), we log the error
        // but still proceed to delete the database row so we don't leave a zombie record.
        val filePath = extractStorageFilePath(scan.imageUrl)
        if (filePath != null) {
            try {
                supabaseClient.storage.from(STORAGE_BUCKET).delete(listOf(filePath))
                Log.d("DELETE_ACTION", "Deleted storage file: $filePath")
            } catch (e: Exception) {
                Log.w("DELETE_ACTION", "Storage delete failed for $filePath, proceeding with DB delete", e)
            }
        }

        // Step 2: Delete the remote database record
        try {
            supabaseClient.from(TABLE_NAME).delete {
                filter {
                    eq("id", scanId)
                }
            }
            Log.d("DELETE_ACTION", "Supabase delete successful")

            // Step 3: ONLY if Supabase DB delete succeeds, delete from local Room cache
            historyDao.deleteHistoryById(scanId)
            Log.d(TAG, "Deleted DB record locally: $scanId")
        } catch (e: Exception) {
            Log.e("DELETE_ACTION", "Supabase delete failed: ${e.message}", e)
            throw e
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
