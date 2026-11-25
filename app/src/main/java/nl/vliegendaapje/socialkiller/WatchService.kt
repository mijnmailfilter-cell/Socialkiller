package nl.vliegendaapje.socialkiller

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import java.time.LocalDate

/**
 * Draait altijd op de achtergrond zodra hij bij Toegankelijkheid is aangezet.
 * Kijkt alleen welke app er op de voorgrond staat; leest geen inhoud.
 *
 * Eén gezamenlijke sessie voor alle social apps:
 *  - tijd telt alleen als een social app echt op het scherm staat (scherm aan, ontgrendeld)
 *  - is er langer dan 3 minuten geen social app open geweest, dan begint de sessie opnieuw
 *  - elk kwartier in de sessie een pop-up
 */
class WatchService : AccessibilityService() {

    companion object {
        @Volatile var instance: WatchService? = null
            private set
        private const val TICK_MS = 2000L
        private const val FLUSH_MS = 30_000L
    }

    private lateinit var prefs: Prefs
    private lateinit var db: UsageDb
    private lateinit var overlay: Overlay
    private val handler = Handler(Looper.getMainLooper())

    private var tracked: Set<String> = emptySet()
    private var currentPkg: String? = null
    private var lastTick = 0L
    private var lastEventPkg: String? = null
    private var lastFlush = 0L

    // in-memory sessie (wordt periodiek naar prefs geschreven)
    private var sessionStart = 0L
    private var activeMs = 0L
    private var nextAlertMs = Session.INTERVAL_MS
    private var alerts = 0
    private var lastSocialAt = 0L

    private val pending = HashMap<String, Long>()

    private val ticker = object : Runnable {
        override fun run() {
            evaluate()
            if (currentPkg != null) handler.postDelayed(this, TICK_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = evaluate()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = Prefs(this)
        db = UsageDb.get(this)
        overlay = Overlay(this) { performGlobalAction(GLOBAL_ACTION_HOME) }
        reloadTracked()

        sessionStart = prefs.sessionStart
        activeMs = prefs.sessionActiveMs
        nextAlertMs = prefs.nextAlertMs
        alerts = prefs.sessionAlerts
        lastSocialAt = prefs.lastSocialAt

        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_EXPORTED)
        evaluate()
    }

    fun reloadTracked() {
        tracked = Prefs(this).trackedPackages()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (pkg != null && !isNoise(pkg)) lastEventPkg = pkg
        }
        evaluate()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        closeCurrent(System.currentTimeMillis())
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.dismiss()
        instance = null
        super.onDestroy()
    }

    private fun isNoise(pkg: String): Boolean =
        pkg == packageName || pkg == "com.android.systemui" ||
            pkg.contains("inputmethod") || pkg.contains("honeyboard") || pkg.contains("keyboard")

    /** Welke app staat er echt op de voorgrond? null als het scherm uit/vergrendeld is. */
    private fun foregroundPkg(): String? {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return null
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) return null
        try {
            val ws = windows
            val app = ws.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }
                ?: ws.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }
            val pkg = app?.root?.packageName?.toString()
            if (pkg != null) return pkg
        } catch (_: Exception) {
        }
        return lastEventPkg
    }

    @Synchronized
    private fun evaluate() {
        if (!::prefs.isInitialized) return
        val now = System.currentTimeMillis()

        // 1) tijd bijschrijven voor de app die tot nu toe open stond
        currentPkg?.let { pkg ->
            val delta = (now - lastTick).coerceIn(0, 10_000) // nooit grote sprongen (slaapstand e.d.)
            activeMs += delta
            pending[pkg] = (pending[pkg] ?: 0) + delta
            lastSocialAt = now
        }
        lastTick = now

        // 2) nieuwe toestand bepalen
        val fg = if (prefs.paused) null else foregroundPkg()
        val social = fg != null && fg in tracked

        if (social) {
            if (currentPkg == null) {
                // (her)intrede: na meer dan 3 min weg begint de sessie opnieuw
                if (sessionStart == 0L || now - lastSocialAt > Session.RESET_AFTER_MS) {
                    sessionStart = now
                    activeMs = 0
                    nextAlertMs = Session.INTERVAL_MS
                    alerts = 0
                }
                lastSocialAt = now
                handler.removeCallbacks(ticker)
                handler.postDelayed(ticker, TICK_MS)
            }
            currentPkg = fg
        } else if (currentPkg != null) {
            closeCurrent(now)
        }

        // 3) pop-up?
        val cur = currentPkg
        if (cur != null && activeMs >= nextAlertMs) {
            while (nextAlertMs <= activeMs) nextAlertMs += Session.INTERVAL_MS
            if (!overlay.isShowing) {
                alerts++
                val level = Messages.levelFor(activeMs)
                overlay.show(Messages.build(level, activeMs, SocialApps.label(this, cur), alerts), prefs.vibrate)
            }
            persist()
        }

        if (now - lastFlush > FLUSH_MS) flush(now)
    }

    private fun closeCurrent(now: Long) {
        if (currentPkg == null) return
        currentPkg = null
        lastSocialAt = now
        handler.removeCallbacks(ticker)
        overlay.dismiss()
        flush(now)
    }

    private fun flush(now: Long) {
        lastFlush = now
        val today = LocalDate.now()
        try {
            pending.forEach { (pkg, ms) -> db.addUsage(today, pkg, ms) }
            pending.clear()
            if (sessionStart > 0) db.saveSession(sessionStart, today, activeMs, alerts)
        } catch (_: Exception) {
        }
        persist()
    }

    private fun persist() {
        prefs.sessionStart = sessionStart
        prefs.sessionActiveMs = activeMs
        prefs.nextAlertMs = nextAlertMs
        prefs.sessionAlerts = alerts
        prefs.lastSocialAt = lastSocialAt
    }

    /** Lopende sessie voor het overzichtsscherm. */
    fun liveSessionMs(): Long = if (currentPkg != null) activeMs else 0L

    fun flushNow() = handler.post { flush(System.currentTimeMillis()) }

    fun showTest(level: Int) {
        val ms = when (level) { 3 -> 95 * 60000L; 2 -> 47 * 60000L; else -> 15 * 60000L }
        handler.post {
            overlay.show(Messages.build(level, ms, "Instagram", level * 3), prefs.vibrate)
        }
    }
}
