package id.local.shopeeextractor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Insert
    suspend fun insertSession(session: CaptureSessionEntity): Long

    @Update
    suspend fun updateSession(session: CaptureSessionEntity)

    @Insert
    suspend fun insertRecords(records: List<TransactionRecordEntity>)

    @Query("SELECT * FROM capture_sessions ORDER BY createdAt DESC")
    fun observeSessions(): Flow<List<CaptureSessionEntity>>

    @Query("SELECT * FROM transaction_records WHERE sessionId = :sessionId ORDER BY sequenceIndex ASC")
    fun observeRecords(sessionId: Long): Flow<List<TransactionRecordEntity>>

    @Query("SELECT * FROM transaction_records WHERE sessionId = :sessionId ORDER BY sequenceIndex ASC")
    suspend fun getRecords(sessionId: Long): List<TransactionRecordEntity>

    @Query("SELECT * FROM capture_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: Long): CaptureSessionEntity?

    @Query("DELETE FROM capture_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)
}
