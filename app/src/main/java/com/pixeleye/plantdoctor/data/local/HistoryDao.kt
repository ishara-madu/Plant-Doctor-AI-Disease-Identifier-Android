package com.pixeleye.plantdoctor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history_table ORDER BY createdAt DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HistoryEntity>)

    @Query("DELETE FROM history_table WHERE id = :id")
    suspend fun deleteHistoryById(id: String)

    @Query("DELETE FROM history_table WHERE id NOT IN (SELECT id FROM history_table ORDER BY createdAt DESC LIMIT 10)")
    suspend fun enforceSizeLimit()

    @Query("DELETE FROM history_table")
    suspend fun clearAll()
}
