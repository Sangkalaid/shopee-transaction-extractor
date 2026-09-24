package id.local.shopeeextractor.csv

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import id.local.shopeeextractor.data.TransactionRecordEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportResult(
    val internalFile: File,
    val publicPathDesc: String,
)

class CsvExporter(private val context: Context) {

    fun buildCsvString(records: List<TransactionRecordEntity>): String {
        return buildString {
            append("\uFEFF") // UTF-8 BOM for Microsoft Excel compatibility
            appendLine("Jenis Transaksi;Keterangan;Tanggal;Nominal;Status")
            records.forEach { record ->
                appendLine(
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
    }

    /**
     * Write CSV to a user-chosen directory via Storage Access Framework (SAF) URI
     */
    fun writeToUri(records: List<TransactionRecordEntity>, targetUri: Uri): Boolean {
        return try {
            val content = buildCsvString(records)
            context.contentResolver.openOutputStream(targetUri)?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Default quick export to internal and public Download directory
     */
    fun export(records: List<TransactionRecordEntity>): ExportResult {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("dd-MM-yyyy_HHmm", Locale.US).format(Date())
        val baseName = "Shopee_Transaksi_$timeStamp"
        val file = uniqueFile(dir, baseName)

        val csvContent = buildCsvString(records)
        file.writeText(csvContent, Charsets.UTF_8)

        var publicDesc = "Folder Download HP (${file.name})"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(csvContent.toByteArray(Charsets.UTF_8))
                    }
                    publicDesc = "Folder Unduhan / Download: ${file.name}"
                }
            } else {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadDir != null && (downloadDir.exists() || downloadDir.mkdirs())) {
                    val publicFile = File(downloadDir, file.name)
                    publicFile.writeText(csvContent, Charsets.UTF_8)
                    publicDesc = "Folder Download: ${publicFile.absolutePath}"
                }
            }
        } catch (e: Exception) {
            // Fallback gracefully to internal file
        }

        return ExportResult(
            internalFile = file,
            publicPathDesc = publicDesc,
        )
    }

    fun shareCsv(file: File) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Bagikan / Buka File CSV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
