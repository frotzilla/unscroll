package com.redwan.unscroll.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Accessibility overlays: the AntiScroll popup, the cooldown screen, and the black covers used to
 * hide parts of a feed. Accessibility overlays need no "draw over apps" permission.
 */
class Overlays(private val service: AccessibilityService) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())

    private var modal: View? = null
    private var modalKind: String? = null
    private var tick: Runnable? = null
    private val covers = mutableListOf<View>()

    val modalShowing get() = modal != null
    fun modalIs(kind: String) = modalKind == kind
    fun modalKind() = modalKind

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), service.resources.displayMetrics).toInt()

    private fun fullParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun text(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(service).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
    }

    private fun button(label: String, filled: Boolean, onClick: () -> Unit) = Button(service).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(if (filled) Color.BLACK else Color.WHITE)
        background = GradientDrawable().apply {
            setColor(if (filled) PINK else Color.TRANSPARENT)
            if (!filled) setStroke(dp(1), Color.GRAY)
            cornerRadius = dp(14).toFloat()
        }
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun column(vararg views: View) = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(28), dp(28), dp(28), dp(28))
        views.forEach { v ->
            addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(if (v is Button) 12 else 18)
                if (v is Button) height = dp(54)
                if (v is android.widget.ProgressBar) { height = dp(4); topMargin = dp(28) }
            })
        }
    }

    private fun showModal(kind: String, content: View) {
        dismissModal()
        val root = FrameLayout(service).apply {
            setBackgroundColor(Color.argb(0xF4, 0, 0, 0))
            isClickable = true // swallow touches meant for the app below
            addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        runCatching { wm.addView(root, fullParams()) }.onSuccess {
            modal = root
            modalKind = kind
        }
    }

    fun dismissModal() {
        tick?.let(main::removeCallbacks)
        tick = null
        modal?.let { runCatching { wm.removeView(it) } }
        modal = null
        modalKind = null
    }

    /**
     * The check-in shown when a scroll limit is reached. "I'm done" is always available;
     * "Continue" unlocks once the wait bar fills.
     */
    fun showCheckIn(headline: String, detail: String?, waitSec: Int, onClose: () -> Unit, onContinue: () -> Unit) {
        val bar = android.widget.ProgressBar(service, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(PINK)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.DKGRAY)
        }
        val cont = button("Keep scrolling", filled = false) { dismissModal(); onContinue() }.apply { isEnabled = false; alpha = 0.4f }
        val views = mutableListOf<View>(
            left(text("CHECK IN", 12f, PINK, bold = true)),
            left(text(headline, 26f, bold = true)),
        )
        if (detail != null) views += left(text(detail, 17f, Color.LTGRAY))
        views += bar
        views += button("Close app", filled = true) { dismissModal(); onClose() }
        views += cont
        showModal("popup", column(*views.toTypedArray()))

        val start = System.currentTimeMillis()
        val total = waitSec * 1000L
        tick = object : Runnable {
            override fun run() {
                val done = System.currentTimeMillis() - start
                bar.progress = if (total <= 0) 1000 else (done * 1000 / total).toInt().coerceAtMost(1000)
                if (done < total) {
                    main.postDelayed(this, 100)
                } else {
                    cont.isEnabled = true
                    cont.alpha = 1f
                }
            }
        }.also { main.post(it) }
    }

    /**
     * A full screen block with a live countdown to [until]: used for lockouts, focus sessions, and
     * used up daily allowances. Dismisses itself when the time runs out.
     */
    fun showTimedBlock(kind: String, tag: String, body: String, until: Long, onHome: () -> Unit) {
        if (modalIs(kind)) return
        val timer = text("", 56f, Color.WHITE, bold = true)
        showModal(kind, column(
            left(text(tag, 12f, PINK, bold = true)),
            left(timer),
            left(text(body, 17f, Color.LTGRAY)),
            button("Leave", filled = true) { dismissModal(); onHome() },
        ))
        tick = object : Runnable {
            override fun run() {
                val left = ((until - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
                timer.text = if (left >= 3600) "%d:%02d:%02d".format(left / 3600, left / 60 % 60, left % 60) else "%d:%02d".format(left / 60, left % 60)
                if (left > 0) main.postDelayed(this, 1000) else dismissModal()
            }
        }.also { main.post(it) }
    }

    /** Asks what [appLabel] is being opened for. The options unlock after two seconds. */
    fun showIntentGate(appLabel: String, onChoice: (browsing: Boolean) -> Unit, onLeave: () -> Unit) {
        if (modalIs("intent")) return
        val choices = listOf(
            "Messages" to false,
            "Post or search" to false,
            "Scroll for 5 min" to true,
        ).map { (label, browsing) ->
            button(label, filled = false) { dismissModal(); onChoice(browsing) }.apply { isEnabled = false; alpha = 0.4f }
        }
        showModal("intent", column(
            left(text("Opening $appLabel", 28f, bold = true)),
            left(text("What for?", 17f, Color.LTGRAY)),
            *choices.toTypedArray(),
            button("Cancel", filled = true) { dismissModal(); onLeave() },
        ))
        tick = Runnable { choices.forEach { it.isEnabled = true; it.alpha = 1f } }.also { main.postDelayed(it, 2000) }
    }

    private fun left(v: TextView) = v.apply { gravity = Gravity.START }

    /**
     * Keeps exactly one black cover per rect. [touchable] covers eat touches (for fully hidden feeds);
     * the others let scrolling pass through.
     */
    fun setCovers(rects: List<Rect>, touchable: Boolean) {
        while (covers.size > rects.size) runCatching { wm.removeView(covers.removeLast()) }
        rects.forEachIndexed { i, r ->
            val p = WindowManager.LayoutParams(
                r.width(), r.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
                PixelFormat.OPAQUE,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = r.left
                y = r.top
            }
            if (i < covers.size) {
                runCatching { wm.updateViewLayout(covers[i], p) }
            } else {
                val v = FrameLayout(service).apply {
                    setBackgroundColor(Color.rgb(8, 8, 8))
                    addView(text("Hidden by Unscroll", 13f, Color.DKGRAY), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                }
                runCatching { wm.addView(v, p) }.onSuccess { covers += v }
            }
        }
    }

    fun clearCovers() = setCovers(emptyList(), false)

    fun clearAll() {
        dismissModal()
        clearCovers()
    }

    companion object {
        val PINK = Color.rgb(0xFF, 0x69, 0xB4)
    }
}
