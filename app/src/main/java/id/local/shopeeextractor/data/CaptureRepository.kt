package id.local.shopeeextractor.data

import id.local.shopeeextractor.parser.ParsedTransaction
import kotlinx.coroutines.flow.Flow

class CaptureRepository(private val dao: CaptureDao) {
    fun observeSessions(): Flow<List<CaptureSessionEntity>> = dao.observeSessions()
    fun observeRecords(sessionId: Long): Flow<List<TransactionRecordEntity>> = dao.observeRecords(sessionId)
    suspend fun getRecords(sessionId: Long): List<TransactionRecordEntity> = dao.getRecords(sessionId)

    suspend fun createSession(): Long =
        dao.insertSession(CaptureSessionEntity(startedAt = System.currentTimeMillis(), status = "RECORDING"))

    suspend fun addTransactions(sessionId: Long, startIndex: Int, transactions: List<ParsedTransaction>) {
        if (transactions.isEmpty()) return
        dao.insertRecords(
            transactions.mapIndexed { offset, transaction ->
                TransactionRecordEntity(
                    sessionId = sessionId,
                    transactionType = transaction.transactionType,
                    description = transaction.description,
                    displayDate = transaction.displayDate,
                    normalizedDate = transaction.normalizedDate,
                    displayAmount = transaction.displayAmount,
                    numericAmount = transaction.numericAmount,
                    transactionStatus = transaction.transactionStatus,
                    internalFingerprint = transaction.contextFingerprint,
                    capturedAt = System.currentTimeMillis(),
                    sequenceIndex = startIndex + offset,
                )
            }
        )
    }

    suspend fun stopSession(sessionId: Long, duplicateSkippedCount: Int, fileName: String = "") {
        val records = dao.getRecords(sessionId)
        val failed = records.count { it.transactionStatus.equals("Gagal", ignoreCase = true) }
        val incoming = records
            .filterNot { it.transactionStatus.equals("Gagal", ignoreCase = true) }
            .filter { it.numericAmount > 0 }
            .sumOf { it.numericAmount }
        val outgoing = records
            .filterNot { it.transactionStatus.equals("Gagal", ignoreCase = true) }
            .filter { it.numericAmount < 0 }
            .sumOf { -it.numericAmount }
        val session = dao.getSession(sessionId) ?: return
        dao.updateSession(
            session.copy(
                stoppedAt = System.currentTimeMillis(),
                totalRecords = records.size,
                totalIncoming = incoming,
                totalOutgoing = outgoing,
                failedCount = failed,
                duplicateSkippedCount = duplicateSkippedCount,
                fileName = fileName,
                status = "STOPPED",
            )
        )
    }

    suspend fun markSaved(sessionId: Long, fileName: String) {
        val session = dao.getSession(sessionId) ?: return
        dao.updateSession(session.copy(fileName = fileName, status = "SAVED"))
    }
}
