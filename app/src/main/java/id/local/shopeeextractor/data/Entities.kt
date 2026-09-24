package id.local.shopeeextractor.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "capture_sessions")
data class CaptureSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val stoppedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val totalRecords: Int = 0,
    val totalIncoming: Long = 0,
    val totalOutgoing: Long = 0,
    val failedCount: Int = 0,
    val duplicateSkippedCount: Int = 0,
    val fileName: String = "",
    val status: String,
)

@Entity(
    tableName = "transaction_records",
    foreignKeys = [
        ForeignKey(
            entity = CaptureSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId"), Index("internalFingerprint")]
)
data class TransactionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val transactionType: String,
    val description: String,
    val displayDate: String,
    val normalizedDate: String,
    val displayAmount: String,
    val numericAmount: Long,
    val transactionStatus: String,
    val internalFingerprint: String,
    val capturedAt: Long,
    val sequenceIndex: Int,
)
