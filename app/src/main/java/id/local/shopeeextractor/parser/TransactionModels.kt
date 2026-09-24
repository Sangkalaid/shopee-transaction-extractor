package id.local.shopeeextractor.parser

data class RawTransactionBlock(
    val lines: List<String>,
    val fullText: String,
    val hierarchyHint: String = "",
    val boundsHint: String = "",
    val visibleIndex: Int = -1,
)

data class ParsedTransaction(
    val transactionType: String,
    val description: String,
    val displayDate: String,
    val normalizedDate: String,
    val displayAmount: String,
    val numericAmount: Long,
    val transactionStatus: String,
    val fullBlockText: String,
    val contextFingerprint: String,
)
