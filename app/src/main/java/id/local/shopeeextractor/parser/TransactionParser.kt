package id.local.shopeeextractor.parser

import java.security.MessageDigest

object TransactionParser {

    private val knownTransactionTypes = listOf(
        "pembayaran",
        "kirim ke bank",
        "transfer ke bank",
        "transfer bank",
        "dana dikembalikan",
        "pengembalian dana",
        "isi saldo",
        "top up",
        "transfer kontak",
        "kirim ke teman",
        "tarik dana",
        "penarikan dana",
        "bonus promosi",
        "tagihan",
        "pulsa & tagihan",
        "spaylater",
        "shopeefood",
        "bagi thr",
        "minta dana",
        "bunga",
        "biaya admin",
        "cashback",
        "penyesuaian saldo",
        "transfer",
        "pembayaran masuk",
        "pembayaran keluar",
        "kirim saldo",
        "terima uang",
        "voucher",
        "refund",
    )

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

    private val noiseTexts = setOf(
        "riwayat transaksi",
        "semua tanggal",
        "metode pembayaran",
        "semua",
        "filter",
        "rincian transaksi",
        "ambil hadiah",
        "ambil hadiah >",
        "ambil hadiah>",
        "lihat rincian",
        "lihat rincian >",
        "lihat rincian>",
        "cek status",
        "cek status >",
        "cek status>",
        "rincian",
        "rincian >",
        "rincian>",
        "beri penilaian",
        "beri penilaian >",
        "beli lagi",
        "beli lagi >",
        "transaksi lainnya",
        "muat lebih banyak",
        ">",
        "<",
        "icon",
        "logo",
        "gambar",
        "image",
    )

    fun isNoise(text: String): Boolean {
        val lower = text.lowercase().trim()
        if (lower.isEmpty()) return true
        if (noiseTexts.contains(lower)) return true
        if (lower == ">" || lower == "<") return true
        if (lower.startsWith("ambil hadiah")) return true
        if (lower.startsWith("lihat rincian")) return true
        if (lower.startsWith("cek status")) return true
        if (lower.startsWith("beri penilaian")) return true
        if (lower.startsWith("beli lagi")) return true
        return false
    }

    fun parse(block: RawTransactionBlock): ParsedTransaction? {
        val rawLines = block.lines
            .flatMap { it.split("\r\n", "\n", "\r") }
            .map { normalizeWhitespace(it) }
            .filter { it.isNotBlank() }
            .filterNot { isNoise(it) }

        if (rawLines.size < 2) return null

        val dateIndex = rawLines.indexOfFirst { IndonesianDateParser.isDate(it) }
        val amountIndex = rawLines.indexOfFirst { AmountParser.isAmount(it) }
        if (dateIndex < 0 || amountIndex < 0) return null

        val displayDate = rawLines[dateIndex]
        val normalizedDate = IndonesianDateParser.normalize(displayDate) ?: return null
        val displayAmount = rawLines[amountIndex]
        val numericAmount = AmountParser.parse(displayAmount) ?: return null

        val status = rawLines
            .filterIndexed { index, _ -> index != dateIndex && index != amountIndex }
            .firstOrNull { knownStatus.contains(it.lowercase()) }
            .orEmpty()

        val candidates = rawLines
            .filterIndexed { index, s ->
                index != dateIndex && index != amountIndex && !knownStatus.contains(s.lowercase())
            }

        var type = ""
        var description = ""

        if (candidates.isEmpty()) {
            return null
        } else if (candidates.size == 1) {
            val single = candidates[0]
            val matchedType = knownTransactionTypes.firstOrNull {
                single.startsWith(it, ignoreCase = true) && single.length > it.length
            }
            if (matchedType != null) {
                type = single.substring(0, matchedType.length).trim()
                description = single.substring(matchedType.length).trim()
            } else {
                type = single
                description = ""
            }
        } else {
            val typeIndex = candidates.indexOfFirst { candidate ->
                knownTransactionTypes.any { kt -> candidate.equals(kt, ignoreCase = true) }
            }
            if (typeIndex >= 0) {
                type = candidates[typeIndex]
                val descParts = candidates.filterIndexed { idx, _ -> idx != typeIndex }
                description = descParts.joinToString(" ")
            } else {
                // If no exact match with known types, check if candidates[0] starts with a known type
                val firstCandidate = candidates[0]
                val matchedPrefix = knownTransactionTypes.firstOrNull {
                    firstCandidate.startsWith(it, ignoreCase = true) && firstCandidate.length > it.length
                }
                if (matchedPrefix != null) {
                    type = firstCandidate.substring(0, matchedPrefix.length).trim()
                    val remainder = firstCandidate.substring(matchedPrefix.length).trim()
                    val otherParts = candidates.drop(1)
                    description = (listOf(remainder).filter { it.isNotBlank() } + otherParts).joinToString(" ")
                } else {
                    type = candidates[0]
                    description = candidates.drop(1).joinToString(" ")
                }
            }
        }

        description = description
            .trim()
            .removePrefix("-")
            .removePrefix(":")
            .removePrefix("•")
            .removePrefix("|")
            .trim()

        val fullText = rawLines.joinToString("\n")
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
                    type,
                    description,
                    normalizedDate,
                    displayAmount,
                    status,
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
