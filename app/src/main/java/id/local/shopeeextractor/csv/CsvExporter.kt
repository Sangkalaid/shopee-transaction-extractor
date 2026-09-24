package id.local.shopeeextractor.csv

import android.content.Context
import id.local.shopeeextractor.data.TransactionRecordEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CsvExporter(private val context: Context) {
    fun export(records: List<TransactionRecordEntity>): File {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val baseName = "Shopee_Transaksi_${SimpleDateFormat("dd-MM-yyyy_HHmm", Locale.US).format(Date())}"
        val file = uniqueFile(dir, baseName)
        file.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.appendLine("Jenis Transaksi;Keterangan;Tanggal;Nominal;Status")
            records.forEach { record ->
                writer.appendLine(
                    listOf(
                        record.transactionType,
                        record.description,
                        record.displayDate,
                        record.displayAmount,
                        record.transactionStatus,
                    ).joinToString(";") { escape(it) }
                )
            }
        }
        return file
    }

    private fun uniqueFile(dir: File, baseName: String): File {
        var candidate = File(dir, "$baseName.csv")
        var index = 2
        while (candidate.exists()) {
            candidate = File(dir, "${baseName}_$index.csv")
            index += 1
        }
        return candidate
    }

    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
