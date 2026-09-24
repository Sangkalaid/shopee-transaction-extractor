package id.local.shopeeextractor.parser

import java.security.MessageDigest

object TransactionParser {
    private val knownStatus = setOf(
        "gagal",
        "berhasil",
        "selesai",
        "dibatalkan",
        "menunggu",
        "menunggu pembayaran",
        "diproses",
        "sukses",
        "dalam proses",
        "dikembalikan",
        "refund",
    )
    private val headerTexts = setOf(
        "riwayat transaksi",
        "semua tanggal",
        "metode pembayaran",
        "semua",
        "filter",
        "pembayaran masuk",
        "pembayaran keluar",
        "rincian transaksi",
    )

    fun parse(block: RawTransactionBlock): ParsedTransaction? {
        val lines = block.lines
            .map { normalizeWhitespace(it) }
            .filter { it.isNotBlank() }
            .filterNot { headerTexts.contains(it.lowercase()) }

        if (lines.size < 3) return null

        val dateIndex = lines.indexOfFirst { IndonesianDateParser.isDate(it) }
        val amountIndex = lines.indexOfFirst { AmountParser.isAmount(it) }
        if (dateIndex <= 0 || amountIndex <= 0) return null

        val type = lines.firstOrNull() ?: return null
        if (type == lines.getOrNull(dateIndex) || type == lines.getOrNull(amountIndex)) return null

        val displayDate = lines[dateIndex]
        val normalizedDate = IndonesianDateParser.normalize(displayDate) ?: return null
        val displayAmount = lines[amountIndex]
        val numericAmount = AmountParser.parse(displayAmount) ?: return null

        val status = lines
            .drop(amountIndex + 1)
            .firstOrNull { knownStatus.contains(it.lowercase()) }
            .orEmpty()

        val description = lines
            .drop(1)
            .takeWhile { it != displayDate && it != displayAmount }
            .filterNot { knownStatus.contains(it.lowercase()) }
            .joinToString(" ")

        val fullText = lines.joinToString("\n")
        return ParsedTransaction(
            transactionType = type,
            description = description,
            displayDate = displayDate,
            normalizedDate = normalizedDate,
            displayAmount = displayAmount,
            numericAmount = numericAmount,
            transactionStatus = status,
            fullBlockText = fullText,
            contextFingerprint = sha256(
                listOf(
                    fullText,
                    block.hierarchyHint,
                    block.boundsHint,
                    block.visibleIndex.toString(),
                ).joinToString("|")
            )
        )
    }

    fun normalizeWhitespace(text: String): String =
        text.replace('\u00A0', ' ')
            .replace("\\s+".toRegex(), " ")
            .trim()

    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
