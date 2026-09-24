package id.local.shopeeextractor.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import id.local.shopeeextractor.MainActivity
import id.local.shopeeextractor.ShopeeExtractorApp
import id.local.shopeeextractor.session.CaptureState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FloatingOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private lateinit var params: WindowManager.LayoutParams

    // Views for Round Bubble Mode & Scroll Animation
    private lateinit var bubbleContainer: LinearLayout
    private lateinit var badgeAnimView: TextView
    private lateinit var bubbleView: LinearLayout
    private lateinit var bubbleIcon: TextView
    private lateinit var bubbleCount: TextView

    // Views for Expanded Menu Card Mode
    private lateinit var menuCardView: LinearLayout
    private lateinit var menuStatusText: TextView
    private lateinit var menuCounterText: TextView
    private lateinit var menuTickerText: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button
    private lateinit var btnCollapse: Button

    private var badgeAnimJob: Job? = null
    private var lastRecordedTimestamp = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createViews()
        observeState()
    }

    override fun onDestroy() {
        badgeAnimJob?.cancel()
        rootView?.let { windowManager?.removeView(it) }
        scope.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun createViews() {
        val container = FrameLayout(this)

        // -------------------------------------------------------------
        // 1. ROUND BUBBLE CONTAINER WITH ANIMATED SCROLL BADGE
        // -------------------------------------------------------------
        bubbleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Animated Pill: Pops up on each scroll when transactions are captured
        badgeAnimView = TextView(this).apply {
            text = "+0 Terbaca! ✓"
            textSize = 11f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            elevation = 24f
            visibility = View.GONE
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0xFF10B981.toInt()) // Vibrant emerald green
                setStroke(dp(1), Color.WHITE)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        // Circular Floating Bubble (62dp)
        bubbleView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(62), dp(62))
            elevation = 16f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xEE1E1E2C.toInt())
                setStroke(dp(2), 0xFF6366F1.toInt()) // Glowing indigo ring
            }
        }

        bubbleIcon = TextView(this).apply {
            text = "▶"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        bubbleCount = TextView(this).apply {
            text = "0"
            textSize = 11f
            setTextColor(0xFF81C784.toInt())
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        bubbleView.addView(bubbleIcon)
        bubbleView.addView(bubbleCount)

        bubbleContainer.addView(badgeAnimView)
        bubbleContainer.addView(bubbleView)

        // -------------------------------------------------------------
        // 2. EXPANDED MENU CARD VIEW (Pilihan Menu Saat Tombol Bulat Diklik)
        // -------------------------------------------------------------
        menuCardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = FrameLayout.LayoutParams(dp(250), FrameLayout.LayoutParams.WRAP_CONTENT)
            elevation = 20f
            visibility = View.GONE
            background = GradientDrawable().apply {
                setColor(0xF81E1E2E.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0xFF475569.toInt())
            }
        }

        // Header: Title & Close Button
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "Shopee Extractor"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = TextView(this).apply {
            text = "✕"
            setTextColor(0xFF94A3B8.toInt())
            textSize = 16f
            setPadding(dp(8), dp(4), dp(4), dp(4))
            setOnClickListener { stopSelf() }
        }

        header.addView(title)
        header.addView(btnClose)
        menuCardView.addView(header)

        // Status & Live Counter
        menuStatusText = TextView(this).apply {
            text = "Status: 🟢 Siap Rekam"
            setTextColor(0xFF81C784.toInt())
            textSize = 12f
            setPadding(0, dp(6), 0, dp(2))
        }
        menuCardView.addView(menuStatusText)

        menuCounterText = TextView(this).apply {
            text = "Data ditemukan: 0"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        }
        menuCardView.addView(menuCounterText)

        // Live Scroll Ticker Feedback
        menuTickerText = TextView(this).apply {
            text = "⚡ Siap membaca scroll transaksi"
            setTextColor(0xFF94A3B8.toInt())
            textSize = 11f
            setPadding(0, 0, 0, dp(10))
        }
        menuCardView.addView(menuTickerText)

        // Menu Options Buttons
        // 1. START Button
        btnStart = Button(this).apply {
            text = "▶ START CAPTURE"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(0xFF2563EB.toInt())
            setTextColor(Color.WHITE)
        }
        menuCardView.addView(btnStart)

        // 2. STOP Button
        btnStop = Button(this).apply {
            text = "⏹ STOP (SELESAI)"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(0xFFDC2626.toInt())
            setTextColor(Color.WHITE)
            visibility = View.GONE
        }
        menuCardView.addView(btnStop)

        // 3. SIMPAN CSV Button (Aktif saat sudah di-stop)
        btnSave = Button(this).apply {
            text = "💾 SIMPAN & PILIH FOLDER CSV"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(0xFF16A34A.toInt())
            setTextColor(Color.WHITE)
            visibility = View.GONE
        }
        menuCardView.addView(btnSave)

        // 4. RESET Button (Kembali ke 0)
        btnReset = Button(this).apply {
            text = "🔄 RESET KE 0"
            textSize = 11f
            setBackgroundColor(0xFF475569.toInt())
            setTextColor(Color.WHITE)
        }
        menuCardView.addView(btnReset)

        // 5. Minimize / Kembali ke Bulat
        btnCollapse = Button(this).apply {
            text = "🔽 Perkecil ke Tombol Bulat"
            textSize = 10f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(0xFF94A3B8.toInt())
            setOnClickListener { showBubbleMode() }
        }
        menuCardView.addView(btnCollapse)

        container.addView(bubbleContainer)
        container.addView(menuCardView)

        // Layout Parameters for WindowManager
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(180)
        }

        // Dragging & Click Handling for the Round Bubble
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var isDragging = false

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (touchX - event.rawX).toInt()
                    val deltaY = (event.rawY - touchY).toInt()
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isDragging = true
                        params.x = (initialX + deltaX).coerceAtLeast(0)
                        params.y = (initialY + deltaY).coerceAtLeast(0)
                        windowManager?.updateViewLayout(container, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        showMenuMode()
                    }
                    true
                }
                else -> false
            }
        }

        rootView = container
        windowManager?.addView(container, params)
    }

    private fun showMenuMode() {
        bubbleContainer.visibility = View.GONE
        menuCardView.visibility = View.VISIBLE
    }

    private fun showBubbleMode() {
        menuCardView.visibility = View.GONE
        bubbleContainer.visibility = View.VISIBLE
    }

    /**
     * Animasi feedback instan setiap kali item transaksi baru berhasil dibaca saat scrolling.
     */
    private fun triggerScrollCaptureAnimation(newCount: Int) {
        // 1. Tactile Haptic Vibration
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(40)
            }
        } catch (e: Exception) {
            bubbleView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        // 2. Bubble Bounce & Bright Green Glow Stroke Animation
        bubbleView.animate()
            .scaleX(1.22f)
            .scaleY(1.22f)
            .setDuration(120)
            .withEndAction {
                bubbleView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(160)
                    .start()
            }
            .start()

        (bubbleView.background as? GradientDrawable)?.apply {
            setStroke(dp(3), 0xFF10B981.toInt()) // Flash emerald border
            bubbleView.postDelayed({
                setStroke(dp(2), 0xFFEF4444.toInt()) // Restore red border
            }, 350)
        }

        // 3. Floating Badge Animation ("+N Terbaca! ✓")
        badgeAnimJob?.cancel()
        badgeAnimView.text = "+$newCount Terbaca! ✓"
        badgeAnimView.visibility = View.VISIBLE
        badgeAnimView.alpha = 0f
        badgeAnimView.scaleX = 0.7f
        badgeAnimView.scaleY = 0.7f
        badgeAnimView.translationY = dp(8).toFloat()

        badgeAnimView.animate()
            .alpha(1f)
            .scaleX(1.08f)
            .scaleY(1.08f)
            .translationY(0f)
            .setDuration(150)
            .withEndAction {
                badgeAnimView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(80)
                    .start()
            }
            .start()

        // 4. Update Ticker Text in Expanded Card
        menuTickerText.text = "✨ Terakhir: +$newCount transaksi terbaca saat scroll"
        menuTickerText.setTextColor(0xFF10B981.toInt())
        menuTickerText.animate()
            .scaleX(1.04f)
            .scaleY(1.04f)
            .setDuration(120)
            .withEndAction {
                menuTickerText.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            .start()

        badgeAnimJob = scope.launch {
            delay(1000)
            badgeAnimView.animate()
                .alpha(0f)
                .translationY(-dp(10).toFloat())
                .setDuration(250)
                .withEndAction {
                    badgeAnimView.visibility = View.GONE
                }
                .start()
        }
    }

    private fun observeState() {
        val app = application as ShopeeExtractorApp

        scope.launch {
            app.coordinator.state.collect { state ->
                // Update live counters
                bubbleCount.text = state.uniqueCount.toString()
                menuCounterText.text = "Data ditemukan: ${state.uniqueCount}"

                // Trigger scroll animation whenever new items are captured during RECORDING
                if (state.state == CaptureState.RECORDING &&
                    state.lastBatchCount > 0 &&
                    state.lastBatchTimestamp > lastRecordedTimestamp
                ) {
                    lastRecordedTimestamp = state.lastBatchTimestamp
                    triggerScrollCaptureAnimation(state.lastBatchCount)
                }

                when (state.state) {
                    CaptureState.RECORDING -> {
                        // Bubble style
                        bubbleIcon.text = "🔴"
                        (bubbleView.background as? GradientDrawable)?.setStroke(dp(2), 0xFFEF4444.toInt())

                        // Menu style
                        menuStatusText.text = "Status: 🔴 RECORDING (Scrolling...)"
                        menuStatusText.setTextColor(0xFFEF4444.toInt())

                        btnStart.visibility = View.GONE
                        btnStop.visibility = View.VISIBLE
                        btnStop.setOnClickListener { app.coordinator.stop() }

                        btnSave.visibility = View.GONE
                        btnReset.visibility = View.VISIBLE
                        btnReset.setOnClickListener { app.coordinator.reset() }
                    }
                    CaptureState.STOPPED -> {
                        // Bubble style
                        bubbleIcon.text = "⏹"
                        (bubbleView.background as? GradientDrawable)?.setStroke(dp(2), 0xFF3B82F6.toInt())

                        // Menu style
                        menuStatusText.text = "Status: ⏹ Selesai (${state.uniqueCount} data)"
                        menuStatusText.setTextColor(0xFF60A5FA.toInt())
                        menuTickerText.text = "Sesi dihentikan. Siap disimpan ke CSV."
                        menuTickerText.setTextColor(0xFF94A3B8.toInt())

                        btnStart.visibility = View.VISIBLE
                        btnStart.text = "▶ MULAI LAGI"
                        btnStart.setOnClickListener { app.coordinator.start() }

                        btnStop.visibility = View.GONE

                        btnSave.visibility = View.VISIBLE
                        btnSave.setOnClickListener {
                            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(intent)
                            Toast.makeText(applicationContext, "Buka aplikasi untuk memilih folder simpan CSV", Toast.LENGTH_SHORT).show()
                            showBubbleMode()
                        }

                        btnReset.visibility = View.VISIBLE
                        btnReset.setOnClickListener {
                            app.coordinator.reset()
                            Toast.makeText(applicationContext, "Data direset ke 0", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        // READY or IDLE
                        bubbleIcon.text = "▶"
                        (bubbleView.background as? GradientDrawable)?.setStroke(dp(2), 0xFF10B981.toInt())

                        menuStatusText.text = "Status: 🟢 Siap Rekam"
                        menuStatusText.setTextColor(0xFF10B981.toInt())
                        menuTickerText.text = "⚡ Siap membaca scroll transaksi"
                        menuTickerText.setTextColor(0xFF94A3B8.toInt())

                        btnStart.visibility = View.VISIBLE
                        btnStart.text = "▶ START CAPTURE"
                        btnStart.setOnClickListener { app.coordinator.start() }

                        btnStop.visibility = View.GONE
                        btnSave.visibility = View.GONE

                        btnReset.visibility = View.VISIBLE
                        btnReset.setOnClickListener { app.coordinator.reset() }
                    }
                }
            }
        }
    }
}
