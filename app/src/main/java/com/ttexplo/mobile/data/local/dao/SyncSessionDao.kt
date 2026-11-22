package com.ttexplo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ttexplo.mobile.data.local.entity.SyncSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SyncSessionEntity)

    @Query("SELECT * FROM sync_sessions ORDER BY timestamp_epoch_ms DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int = 50): Flow<List<SyncSessionEntity>>

    @Query("SELECT * FROM sync_sessions ORDER BY timestamp_epoch_ms DESC")
    suspend fun getAllSessions(): List<SyncSessionEntity>
}
