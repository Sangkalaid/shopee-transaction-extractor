package id.local.shopeeextractor

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
                    color = Color(0xFFF8F9FA)
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

    var showPermissionPromptDialog by rememberSaveable { mutableStateOf(false) }
    var selectedSessionForCustomDir by remember { mutableStateOf<CaptureSessionEntity?>(null) }
    var showSuccessLaunchDialog by remember { mutableStateOf(false) }

    val manufacturer = remember { Build.MANUFACTURER.replaceFirstChar { it.uppercase() } }

    // Launcher for Storage Access Framework (SAF) - allows user to pick ANY directory on device
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val session = selectedSessionForCustomDir
        if (uri != null && session != null) {
            scope.launch {
                val records = app.repository.getRecords(session.id)
                val success = CsvExporter(context).writeToUri(records, uri)
                if (success) {
                    app.repository.markSaved(session.id, "Disimpan di folder pilihan")
                    Toast.makeText(context, "✅ Berhasil disimpan ke folder yang Anda pilih!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Gagal menyimpan file ke lokasi tersebut.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        selectedSessionForCustomDir = null
    }

    // Auto-prompt dialog on launch if any permission is missing
    LaunchedEffect(Unit) {
        if (!isOverlayActive || !isAccessibilityActive) {
            showPermissionPromptDialog = true
        }
    }

    // Real-time tracking of permissions on resume
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

    // Unified scrollable layout for seamless UI across all device sizes
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // App Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Shopee Extractor",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    )
                    Text(
                        text = "Perangkat: $manufacturer",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                }
                Box(
                    modifier = Modifier
                        .background(
                            if (isAllReady) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isAllReady) "● SIAP DIGUNAKAN" else "● PERLU PERIZINAN",
                        color = if (isAllReady) Color(0xFF2E7D32) else Color(0xFFE65100),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Section 1: Floating Bubble Feature Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF2563EB), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔘", fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Tombol Bulat Melayang Aktif",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF1E40AF)
                        )
                        Text(
                            "Saat tombol bulat diklik, muncul menu: START, STOP, SIMPAN CSV, dan RESET ke 0.",
                            fontSize = 11.sp,
                            color = Color(0xFF1D4ED8)
                        )
                    }
                }
            }
        }

        // Section 2: Permission Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Status Perizinan Aplikasi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF1E293B)
                    )

                    PermissionStatusRow("Tombol Bulat Melayang (Overlay)", isOverlayActive)
                    PermissionStatusRow("Layanan Aksesibilitas Shopee", isAccessibilityActive)

                    if (!isAccessibilityActive) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                            border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⚠️ ", fontSize = 13.sp)
                                    Text(
                                        "Tombol Aksesibilitas Abu-Abu / Dibatasi?",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFFC2410C)
                                    )
                                }
                                Text(
                                    "Android 13/14/15 mengunci aksesibilitas aplikasi baru demi keamanan. Cara membukanya:",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9A3412)
                                )
                                Text(
                                    "1. Klik tombol 'Buka Info Aplikasi' di bawah.\n2. Klik Titik Tiga (⋮) di pojok kanan atas layar Info Aplikasi.\n3. Pilih 'Izinkan setelan terbatas' lalu masukkan PIN/kunci layar.\n4. Buka menu Aksesibilitas dan geser tombol menjadi aktif.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF7C2D12),
                                    lineHeight = 16.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                                        onClick = {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        }
                                    ) {
                                        Text("🔓 1. Info Aplikasi", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(6.dp),
                                        onClick = {
                                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                        }
                                    ) {
                                        Text("⚙️ 2. Aksesibilitas", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (!isAllReady) {
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            onClick = { showPermissionPromptDialog = true }
                        ) {
                            Text("Tampilkan Dialog Perizinan (Izinkan / Tolak)", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Section 3: Primary Action Button
        item {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAllReady) Color(0xFF2563EB) else Color(0xFF94A3B8)
                ),
                shape = RoundedCornerShape(10.dp),
                onClick = {
                    if (!isAllReady) {
                        showPermissionPromptDialog = true
                    } else {
                        app.coordinator.ready()
                        context.startService(Intent(context, FloatingOverlayService::class.java))
                        showSuccessLaunchDialog = true
                    }
                }
            ) {
                Text(
                    "MUNCULKAN TOMBOL BULAT DI LAYAR",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // Section 4: History & Export Sessions Header
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "DATA REKAMAN TERSIMPAN",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF475569)
                )
            )
        }

        if (sessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Belum ada data rekaman. Munculkan tombol bulat untuk mulai merekam transaksi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        } else {
            items(sessions) { session ->
                SessionCard(
                    session = session,
                    onQuickExport = {
                        scope.launch {
                            val records = app.repository.getRecords(session.id)
                            val exporter = CsvExporter(context)
                            val result = exporter.export(records)
                            app.repository.markSaved(session.id, result.internalFile.name)
                            Toast.makeText(context, "Tersimpan di: ${result.publicPathDesc}", Toast.LENGTH_LONG).show()
                            exporter.shareCsv(result.internalFile)
                        }
                    },
                    onCustomDirectoryExport = {
                        selectedSessionForCustomDir = session
                        val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date(session.createdAt))
                        createDocumentLauncher.launch("Shopee_Transaksi_${dateStr}.csv")
                    }
                )
            }
        }
    }

    // -------------------------------------------------------------
    // DIALOG: Auto / Interactive Permission Prompt (Izinkan / Tolak)
    // -------------------------------------------------------------
    if (showPermissionPromptDialog) {
        val nextStepIsOverlay = !isOverlayActive
        val nextStepIsAccessibility = isOverlayActive && !isAccessibilityActive

        AlertDialog(
            onDismissRequest = { showPermissionPromptDialog = false },
            title = {
                Text(
                    if (nextStepIsOverlay) "Izinkan Tombol Bulat Melayang?"
                    else "Izinkan Layanan Aksesibilitas Shopee?"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (nextStepIsOverlay) {
                        Text("Aplikasi memerlukan izin untuk menampilkan tombol bulat melayang di atas aplikasi Shopee.")
                        Text(
                            "Tekan 'Izinkan' untuk mengaktifkannya secara langsung.",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )
                    } else if (nextStepIsAccessibility) {
                        Text("Aplikasi memerlukan layanan aksesibilitas untuk membaca riwayat transaksi Shopee Anda.")
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                            border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "💡 Jika muncul 'Setelan Dibatasi' atau tombol abu-abu:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFFC2410C)
                                )
                                Text(
                                    "1. Klik tombol '1. Info Aplikasi' di bawah.\n2. Klik Titik Tiga (⋮) di pojok kanan atas.\n3. Pilih 'Izinkan setelan terbatas' & masukkan kunci layar.\n4. Klik tombol '2. Aksesibilitas' dan aktifkan.",
                                    fontSize = 10.sp,
                                    color = Color(0xFF7C2D12),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    } else {
                        Text("Semua perizinan telah aktif! Aplikasi siap digunakan.")
                    }
                }
            },
            confirmButton = {
                if (nextStepIsAccessibility) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                            shape = RoundedCornerShape(8.dp),
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text("1. Buka Info Aplikasi (Buka Kunci)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            }
                        ) {
                            Text("2. Buka Menu Aksesibilitas", fontSize = 11.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            if (nextStepIsOverlay) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                showPermissionPromptDialog = false
                            }
                        }
                    ) {
                        Text(if (isAllReady) "Selesai" else "Izinkan")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionPromptDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    // -------------------------------------------------------------
    // DIALOG: Floating Bubble Ready Guide
    // -------------------------------------------------------------
    if (showSuccessLaunchDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessLaunchDialog = false },
            title = { Text("Tombol Bulat Melayang Aktif! 🔘") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tombol bulat telah muncul di layar Anda:")
                    Text("1. Klik tombol bulat untuk memunculkan pilihan: START, STOP, SIMPAN CSV, dan RESET.")
                    Text("2. Buka Shopee > Riwayat Transaksi.")
                    Text("3. Klik START lalu scroll riwayat transaksi.")
                    Text("4. Setelah STOP, pilih SIMPAN CSV untuk memilih folder penyimpanan di HP Anda.")
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
private fun PermissionStatusRow(title: String, isActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 12.sp, color = Color(0xFF334155))
        Text(
            text = if (isActive) "✓ Diizinkan" else "✗ Belum Diizinkan",
            color = if (isActive) Color(0xFF16A34A) else Color(0xFFDC2626),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SessionCard(
    session: CaptureSessionEntity,
    onQuickExport: () -> Unit,
    onCustomDirectoryExport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    session.fileName.ifBlank { "Sesi Rekaman #${session.id}" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "${session.totalRecords} Transaksi",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2563EB)
                )
            }
            Text(formatDate(session.createdAt), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text(
                "Masuk: Rp ${session.totalIncoming} | Keluar: Rp ${session.totalOutgoing}",
                fontSize = 12.sp,
                color = Color(0xFF334155)
            )

            // Two Export Choices: SAF Directory Picker or Quick Download
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    onClick = onCustomDirectoryExport
                ) {
                    Text("📁 Pilih Folder HP", fontSize = 11.sp)
                }
                Button(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    onClick = onQuickExport
                ) {
                    Text("Simpan & Share", fontSize = 11.sp)
                }
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
