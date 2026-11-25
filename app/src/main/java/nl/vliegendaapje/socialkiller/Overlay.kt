package nl.vliegendaapje.socialkiller

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Toont de pop-up als accessibility-overlay (geen extra "over andere apps"-toestemming nodig).
 * Geen geluid. Bij niveau 3 (eventueel) een korte trilling.
 */
class Overlay(private val ctx: Context, private val onLeave: () -> Unit) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var timer: CountDownTimer? = null

    val isShowing get() = view != null

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    fun show(p: Messages.Popup, vibrate: Boolean) {
        dismiss()
        val accent = when (p.level) { 3 -> 0xFFE5484D.toInt(); 2 -> 0xFFF5A524.toInt(); else -> 0xFF7C5CFF.toInt() }

        val root = FrameLayout(ctx).apply {
            setBackgroundColor(if (p.level == 3) 0xF21E1B2E.toInt() else 0x99000000.toInt())
            // klikken naast de kaart doet niets: je moet bewust een knop kiezen
            isClickable = true
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(if (p.level == 3) 0xFF2A2640.toInt() else Color.WHITE)
            }
            elevation = dp(8).toFloat()
        }
        val textColor = if (p.level == 3) Color.WHITE else 0xFF1E1B2E.toInt()

        val emoji = TextView(ctx).apply {
            text = when (p.level) { 3 -> "🚨"; 2 -> "😬"; else -> "👀" }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (p.level == 3) 56f else 40f)
            gravity = Gravity.CENTER
        }
        val title = TextView(ctx).apply {
            text = p.title
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (p.level == 3) 26f else 20f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        val body = TextView(ctx).apply {
            text = p.body
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (p.level == 3) 20f else 17f)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding(0, 0, 0, dp(20))
        }

        fun pill(label: String, filled: Boolean): Button = Button(ctx).apply {
            text = label
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            stateListAnimator = null
            setTextColor(if (filled) Color.WHITE else accent)
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                if (filled) setColor(accent) else {
                    setColor(Color.TRANSPARENT); setStroke(dp(2), accent)
                }
            }
            minHeight = dp(52)
        }

        val leaveBtn = pill(p.leave, true)
        val stayBtn = pill(p.stay, false)
        leaveBtn.setOnClickListener { dismiss(); onLeave() }
        stayBtn.setOnClickListener { dismiss() }

        val lpBtn = { top: Int ->
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = top }
        }
        card.addView(emoji)
        card.addView(title)
        card.addView(body)
        card.addView(leaveBtn, lpBtn(0))
        card.addView(stayBtn, lpBtn(dp(10)))

        root.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ).apply { setMargins(dp(24), 0, dp(24), 0) })

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        try {
            wm.addView(root, lp)
            view = root
        } catch (e: Exception) {
            view = null
            return
        }

        if (p.level == 3) {
            // "Doorgaan" pas na 5 seconden aanklikbaar
            val label = p.stay
            stayBtn.isEnabled = false
            stayBtn.alpha = 0.5f
            timer = object : CountDownTimer(5000, 250) {
                override fun onTick(left: Long) {
                    stayBtn.text = "Even wachten… ${(left + 999) / 1000}"
                }
                override fun onFinish() {
                    stayBtn.text = label
                    stayBtn.isEnabled = true
                    stayBtn.alpha = 1f
                }
            }.start()
            if (vibrate) buzz()
        }
    }

    private fun buzz() {
        try {
            val v: Vibrator = if (android.os.Build.VERSION.SDK_INT >= 31)
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else
                @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 120, 120, 120, 300), -1))
        } catch (_: Exception) {
        }
    }

    fun dismiss() {
        timer?.cancel(); timer = null
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
    }
}
