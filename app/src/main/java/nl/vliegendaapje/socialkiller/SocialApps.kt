package nl.vliegendaapje.socialkiller

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class KnownApp(val name: String, val packages: List<String>)

object SocialApps {
    /** Standaardlijst. Meerdere pakketnamen per app (Lite-versies, regionale varianten). */
    val known: List<KnownApp> = listOf(
        KnownApp("Facebook", listOf("com.facebook.katana", "com.facebook.lite")),
        KnownApp("Instagram", listOf("com.instagram.android", "com.instagram.lite")),
        KnownApp("TikTok", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go")),
        KnownApp("Imgur", listOf("com.imgur.mobile")),
        KnownApp("YouTube", listOf("com.google.android.youtube")),
        KnownApp("Reddit", listOf("com.reddit.frontpage")),
        KnownApp("X (Twitter)", listOf("com.twitter.android")),
        KnownApp("Pinterest", listOf("com.pinterest")),
        KnownApp("LinkedIn", listOf("com.linkedin.android")),
        KnownApp("Snapchat", listOf("com.snapchat.android")),
        KnownApp("Threads", listOf("com.instagram.barcelona")),
    )

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun label(ctx: Context, pkg: String): String {
        known.firstOrNull { pkg in it.packages }?.let { return it.name }
        return try {
            val info = ctx.packageManager.getApplicationInfo(pkg, 0)
            ctx.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    /** Alle apps met een icoon in de app-lade (voor "App toevoegen"). */
    fun launchableApps(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != ctx.packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }
}
