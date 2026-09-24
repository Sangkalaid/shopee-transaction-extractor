package id.local.shopeeextractor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.local.shopeeextractor.csv.CsvExporter
import id.local.shopeeextractor.data.CaptureSessionEntity
import id.local.shopeeextractor.overlay.FloatingOverlayService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen()
                }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as ShopeeExtractorApp
    val sessions by app.repository.observeSessions().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Shopee Transaction Extractor", style = MaterialTheme.typography.headlineSmall)
        PermissionRow(
            title = "Accessibility",
            active = context.isAccessibilityEnabled(),
            action = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )
        PermissionRow(
            title = "Overlay",
            active = Settings.canDrawOverlays(context),
            action = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                app.coordinator.ready()
                context.startService(Intent(context, FloatingOverlayService::class.java))
            },
        ) {
            Text("MULAI")
        }
        Spacer(Modifier.height(8.dp))
        Text("FILE TERSIMPAN", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sessions) { session ->
                SessionCard(
                    session = session,
                    onExport = {
                        scope.launch {
                            val records = app.repository.getRecords(session.id)
                            val file = CsvExporter(context).export(records)
                            app.repository.markSaved(session.id, file.name)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, active: Boolean, action: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("$title: ${if (active) "Aktif" else "Belum Aktif"}")
        Button(onClick = action) {
            Text(if (active) "Buka" else "Aktifkan")
        }
    }
}

@Composable
private fun SessionCard(session: CaptureSessionEntity, onExport: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(session.fileName.ifBlank { "Session ${session.id}" }, style = MaterialTheme.typography.titleSmall)
            Text("${session.totalRecords} transaksi")
            Text(formatDate(session.createdAt))
            Text("Masuk: Rp ${session.totalIncoming} | Keluar: Rp ${session.totalOutgoing}")
            Text("Gagal: ${session.failedCount} | Duplikat dilewati: ${session.duplicateSkippedCount}")
            Button(onClick = onExport) {
                Text("Export / Simpan CSV")
            }
        }
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date(millis))

private fun Context.isAccessibilityEnabled(): Boolean {
    val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == packageName }
}
