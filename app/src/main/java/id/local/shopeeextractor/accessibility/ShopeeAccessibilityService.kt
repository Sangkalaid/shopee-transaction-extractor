package id.local.shopeeextractor.accessibility

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import id.local.shopeeextractor.ShopeeExtractorApp
import id.local.shopeeextractor.parser.TransactionParser
import id.local.shopeeextractor.session.CaptureState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ShopeeAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var parseJob: Job? = null
    private var buttonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val controller = accessibilityButtonController
            val callback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController?) {
                    val app = application as? ShopeeExtractorApp ?: return
                    when (app.coordinator.state.value.state) {
                        CaptureState.RECORDING -> {
                            app.coordinator.stop()
                            Toast.makeText(
                                applicationContext,
                                "⏹ Perekaman Transaksi Shopee Dihentikan",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        else -> {
                            app.coordinator.start()
                            Toast.makeText(
                                applicationContext,
                                "🔴 Perekaman Transaksi Shopee Dimulai...",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            buttonCallback = callback
            controller.registerAccessibilityButtonCallback(callback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val app = application as? ShopeeExtractorApp ?: return
        if (app.coordinator.state.value.state != CaptureState.RECORDING) return

        val packageName = event?.packageName?.toString().orEmpty()
        if (packageName.isNotBlank() && !isShopeePackage(packageName)) return

        parseJob?.cancel()
        parseJob = scope.launch {
            delay(180)
            val root = rootInActiveWindow ?: return@launch
            val rootPkg = root.packageName?.toString().orEmpty()
            if (rootPkg.isNotBlank() && !isShopeePackage(rootPkg)) return@launch

            val parsed = AccessibilityNodeReader.extractCandidateBlocks(root)
                .mapNotNull { TransactionParser.parse(it) }
            app.coordinator.onVisibleTransactions(parsed)
        }
    }

    private fun isShopeePackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("shopee") ||
                lower.contains("airpay") ||
                lower.contains("seabank")
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        buttonCallback?.let { callback ->
            try {
                accessibilityButtonController.unregisterAccessibilityButtonCallback(callback)
            } catch (e: Exception) {
                // Ignored
            }
        }
        super.onDestroy()
    }
}
