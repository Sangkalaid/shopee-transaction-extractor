package id.local.shopeeextractor.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import id.local.shopeeextractor.ShopeeExtractorApp
import id.local.shopeeextractor.session.CaptureState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var view: View? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
        observeState()
    }

    override fun onDestroy() {
        view?.let { windowManager?.removeView(it) }
        scope.cancel()
        super.onDestroy()
    }

    private fun showOverlay() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            setBackgroundColor(0xEEFFFFFF.toInt())
            elevation = 8f
        }
        val title = TextView(this).apply { text = "Shopee Extractor" }
        val status = TextView(this).apply { id = View.generateViewId() }
        val counter = TextView(this).apply { id = View.generateViewId() }
        val primary = Button(this).apply { id = View.generateViewId() }
        val close = Button(this).apply {
            text = "TUTUP FLOATING BUTTON"
            setOnClickListener { stopSelf() }
        }
        root.addView(title)
        root.addView(status)
        root.addView(counter)
        root.addView(primary)
        root.addView(close)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (initialX - (event.rawX - touchX)).toInt().coerceAtLeast(0)
                    params.y = (initialY + (event.rawY - touchY)).toInt().coerceAtLeast(0)
                    windowManager?.updateViewLayout(root, params)
                    true
                }
                else -> false
            }
        }

        view = root
        windowManager?.addView(root, params)
    }

    private fun observeState() {
        val app = application as ShopeeExtractorApp
        val root = view as LinearLayout
        val status = root.getChildAt(1) as TextView
        val counter = root.getChildAt(2) as TextView
        val primary = root.getChildAt(3) as Button

        scope.launch {
            app.coordinator.state.collect { state ->
                status.text = "Status: ${state.state}"
                counter.text = "Data ditemukan: ${state.uniqueCount}"
                when (state.state) {
                    CaptureState.RECORDING -> {
                        primary.text = "BERHENTI"
                        primary.setOnClickListener { app.coordinator.stop() }
                    }
                    CaptureState.STOPPED -> {
                        primary.text = "SIMPAN CSV DI APLIKASI"
                        primary.setOnClickListener { stopSelf() }
                    }
                    else -> {
                        primary.text = "START"
                        primary.setOnClickListener { app.coordinator.start() }
                    }
                }
            }
        }
    }
}
