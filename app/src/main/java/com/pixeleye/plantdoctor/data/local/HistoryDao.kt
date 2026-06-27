package com.pixeleye.plantdoctor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history_table ORDER BY createdAt DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_table")
    suspend fun getAllHistoryOnce(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HistoryEntity>)

    @Query("DELETE FROM history_table WHERE id = :id")
    suspend fun deleteHistoryById(id: String)

    @Query("""
        DELETE FROM history_table 
        WHERE id NOT IN (
            SELECT id FROM history_table 
            WHERE parentId IS NULL OR parentId = '' 
            ORDER BY createdAt DESC LIMIT 10
        )
        AND (
            parentId IS NULL 
            OR parentId = '' 
            OR parentId NOT IN (
                SELECT id FROM history_table 
                WHERE parentId IS NULL OR parentId = '' 
                ORDER BY createdAt DESC LIMIT 10
            )
        )
    """)
    suspend fun enforceSizeLimit()

    @Transaction
    suspend fun insertAllAndEnforceLimit(items: List<HistoryEntity>) {
        insertAll(items)
        enforceSizeLimit()
    }

    @Transaction
    suspend fun insertHistoryAndEnforceLimit(item: HistoryEntity) {
        insertHistory(item)
        enforceSizeLimit()
    }

    @Query("DELETE FROM history_table")
    suspend fun clearAll()

    @Query("SELECT * FROM history_table WHERE id = :parentId OR parentId = :parentId ORDER BY createdAt ASC")
    suspend fun getThreadScans(parentId: String): List<HistoryEntity>

    @Query("SELECT * FROM history_table WHERE parentId = :parentId")
    suspend fun getFollowUps(parentId: String): List<HistoryEntity>

    @Query("SELECT * FROM history_table WHERE id = :id")
    suspend fun getHistoryById(id: String): HistoryEntity?

    @Query("UPDATE history_table SET plantName = :plantName WHERE id = :id")
    suspend fun updatePlantName(id: String, plantName: String)
}
