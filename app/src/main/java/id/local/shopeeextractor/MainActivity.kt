package id.local.shopeeextractor

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen()
                }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = context.applicationContext as ShopeeExtractorApp
    val sessions by app.repository.observeSessions().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var isOverlayActive by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAccessibilityActive by remember { mutableStateOf(context.isAccessibilityEnabled()) }

    var showPermissionMissingDialog by remember { mutableStateOf(false) }
    var showSuccessLaunchDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isOverlayActive = Settings.canDrawOverlays(context)
                isAccessibilityActive = context.isAccessibilityEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isAllReady = isOverlayActive && isAccessibilityActive

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Shopee Transaction Extractor",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )

        // Status Banner Card
        if (isAllReady) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✅", fontSize = 24.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Semua Izin Telah Aktif!",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            "Aplikasi siap digunakan untuk merekam transaksi Shopee.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1B5E20)
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                border = BorderStroke(1.dp, Color(0xFFFF9800)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️", fontSize = 24.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Izin Perangkat Diperlukan",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                        Text(
                            "Harap aktifkan 2 izin di bawah agar aplikasi dapat berfungsi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFBF360C)
                        )
                    }
                }
            }
        }

        // Step 1: Overlay Permission Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "1. Izin Tampil di Atas Aplikasi",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isOverlayActive) "AKTIF" else "BELUM AKTIF",
                        fontWeight = FontWeight.Bold,
                        color = if (isOverlayActive) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                    )
                }
                Text(
                    "Dibutuhkan untuk memunculkan tombol kontrol mengambang (floating button) saat Anda membuka Shopee.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
                if (!isOverlayActive) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Buka Pengaturan Overlay")
                    }
                }
            }
        }

        // Step 2: Accessibility Permission Card (with Android 13+ support)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "2. Layanan Aksesibilitas",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isAccessibilityActive) "AKTIF" else "BELUM AKTIF",
                        fontWeight = FontWeight.Bold,
                        color = if (isAccessibilityActive) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                    )
                }
                Text(
                    "Dibutuhkan untuk membaca teks riwayat transaksi yang tampil di layar aplikasi Shopee.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )

                if (!isAccessibilityActive) {
                    // For Android 13+ (API 33+), provide direct shortcut to App Info for Restricted Settings
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Petunjuk Khusus Android 13/14/15:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF4A148C)
                                )
                                Text(
                                    "Jika tombol aksesibilitas tidak bisa diklik (abu-abu/dibatasi), ketuk tombol di bawah, klik titik 3 di kanan atas, lalu pilih 'Izinkan setelan terbatas'.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF311B92)
                                )
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Text("Buka Info Aplikasi (Buka Setelan Terbatas)", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Buka Pengaturan Aksesibilitas")
                    }
                }
            }
        }

        // Primary Action: Start Extraction
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isAllReady) MaterialTheme.colorScheme.primary else Color(0xFF757575)
            ),
            onClick = {
                if (!isAllReady) {
                    showPermissionMissingDialog = true
                } else {
                    app.coordinator.ready()
                    context.startService(Intent(context, FloatingOverlayService::class.java))
                    showSuccessLaunchDialog = true
                }
            }
        ) {
            Text(
                "MULAI CAPTURE TRANSAKSI",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "RIWAYAT DATA TERSIMPAN",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )

        if (sessions.isEmpty()) {
            Text(
                "Belum ada data sesi yang tersimpan. Mulai perekaman untuk mengekstrak transaksi.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
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

    // Modal: Permission Missing Warning
    if (showPermissionMissingDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionMissingDialog = false },
            title = { Text("Izin Belum Lengkap ⚠️") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Aplikasi membutuhkan izin berikut sebelum dapat dijalankan:")
                    if (!isOverlayActive) {
                        Text("• Izin Overlay (Tampil di atas aplikasi lain)")
                    }
                    if (!isAccessibilityActive) {
                        Text("• Layanan Aksesibilitas (Shopee Transaction Extractor)")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Silakan selesaikan izin di atas terlebih dahulu.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showPermissionMissingDialog = false }) {
                    Text("Mengerti")
                }
            }
        )
    }

    // Modal: Instructions after Floating Service launched
    if (showSuccessLaunchDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessLaunchDialog = false },
            title = { Text("Widget Melayang Aktif! 🚀") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Widget tombol kontrol telah muncul di layar Anda.")
                    Text("Langkah berikutnya:")
                    Text("1. Buka aplikasi Shopee > Riwayat Transaksi ShopeePay.")
                    Text("2. Ketuk tombol 'START' pada widget melayang (status berubah jadi RECORDING).")
                    Text("3. Scroll riwayat transaksi perlahan ke bawah.")
                    Text("4. Ketuk 'BERHENTI' setelah selesai.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessLaunchDialog = false
                        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.shopee.id")
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        }
                    }
                ) {
                    Text("Buka Shopee Langsung")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSuccessLaunchDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun SessionCard(session: CaptureSessionEntity, onExport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                session.fileName.ifBlank { "Sesi Rekaman #${session.id}" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text("${session.totalRecords} transaksi ditemukan")
            Text(formatDate(session.createdAt), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("Masuk: Rp ${session.totalIncoming} | Keluar: Rp ${session.totalOutgoing}")
            Text("Gagal: ${session.failedCount} | Duplikat dilewati: ${session.duplicateSkippedCount}")
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExport
            ) {
                Text("Simpan / Ekspor ke File CSV")
            }
        }
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date(millis))

private fun Context.isAccessibilityEnabled(): Boolean {
    val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    val enabledServices = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabledServices.any {
        it.id.contains(packageName, ignoreCase = true) ||
        it.resolveInfo?.serviceInfo?.packageName == packageName
    }
}
