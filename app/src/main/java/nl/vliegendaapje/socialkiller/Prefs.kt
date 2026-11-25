package nl.vliegendaapje.socialkiller

import android.content.Context
import android.content.SharedPreferences

class Prefs(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("socialkiller", Context.MODE_PRIVATE)

    /** Bekende apps die de gebruiker heeft uitgezet (op naam). */
    var disabledKnown: Set<String>
        get() = sp.getStringSet("disabled_known", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("disabled_known", v).apply()

    /** Zelf toegevoegde pakketnamen. */
    var customPackages: Set<String>
        get() = sp.getStringSet("custom_pkgs", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("custom_pkgs", v).apply()

    var vibrate: Boolean
        get() = sp.getBoolean("vibrate", true)
        set(v) = sp.edit().putBoolean("vibrate", v).apply()

    var paused: Boolean
        get() = sp.getBoolean("paused", false)
        set(v) = sp.edit().putBoolean("paused", v).apply()

    fun trackedPackages(): Set<String> {
        val off = disabledKnown
        val set = HashSet<String>()
        SocialApps.known.filter { it.name !in off }.forEach { set.addAll(it.packages) }
        set.addAll(customPackages)
        return set
    }

    // ---- Sessiestatus (zodat een herstart van de service niets kwijtraakt) ----
    var sessionStart: Long
        get() = sp.getLong("s_start", 0L)
        set(v) = sp.edit().putLong("s_start", v).apply()
    var sessionActiveMs: Long
        get() = sp.getLong("s_active", 0L)
        set(v) = sp.edit().putLong("s_active", v).apply()
    var nextAlertMs: Long
        get() = sp.getLong("s_next", Session.INTERVAL_MS)
        set(v) = sp.edit().putLong("s_next", v).apply()
    var sessionAlerts: Int
        get() = sp.getInt("s_alerts", 0)
        set(v) = sp.edit().putInt("s_alerts", v).apply()
    var lastSocialAt: Long
        get() = sp.getLong("s_last", 0L)
        set(v) = sp.edit().putLong("s_last", v).apply()
}

object Session {
    const val INTERVAL_MS = 15 * 60 * 1000L
    const val RESET_AFTER_MS = 3 * 60 * 1000L
}
