package nl.vliegendaapje.socialkiller

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val Ink = Color(0xFF1E1B2E)
private val Purple = Color(0xFF7C5CFF)
private val Red = Color(0xFFE5484D)
private val Soft = Color(0xFFF6F4FB)
private val Muted = Color(0xFF6B6880)
private val NL = Locale("nl", "NL")

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Purple, secondary = Red, background = Soft)) {
                App(resumeTick.intValue)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

fun isServiceEnabled(ctx: Context): Boolean {
    val me = ComponentName(ctx, WatchService::class.java)
    val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return list.split(':').any { ComponentName.unflattenFromString(it) == me }
}

fun isBatteryFree(ctx: Context): Boolean =
    (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

@Composable
fun App(resumeTick: Int) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Soft)) {
        Box(Modifier.fillMaxWidth().background(Ink).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp)) {
            Column {
                Text("Social Killer", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Hoeveel van je leven gaat er naar de feed?", color = Color(0xFFB9B4D0), fontSize = 13.sp)
            }
        }
        TabRow(selectedTabIndex = tab, containerColor = Ink, contentColor = Color.White) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Overzicht") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Instellingen") })
        }
        if (tab == 0) Overview(resumeTick, onGoSettings = { tab = 1 }) else SettingsScreen(resumeTick)
    }
}

// ------------------------------------------------------------------ Overzicht

enum class Range(val label: String) { WEEK("Week"), MONTH("Maand"), YEAR("Jaar") }

data class Bar(val label: String, val full: String, val ms: Long, val highlight: Boolean)

data class Stats(
    val todayMs: Long,
    val bars: List<Bar>,
    val totalMs: Long,
    val avgPerDayMs: Long,
    val longestMs: Long,
    val popups: Int,
    val perApp: List<Pair<String, Long>>,
)

private fun loadStats(ctx: Context, range: Range): Stats {
    val db = UsageDb.get(ctx)
    val today = LocalDate.now()
    val from = when (range) {
        Range.WEEK -> today.minusDays(6)
        Range.MONTH -> today.minusDays(29)
        Range.YEAR -> today.withDayOfMonth(1).minusMonths(11)
    }
    val days = db.perDay(from, today)
    val bars = when (range) {
        Range.WEEK, Range.MONTH -> {
            val n = if (range == Range.WEEK) 7 else 30
            (0 until n).map { i ->
                val d = from.plusDays(i.toLong())
                val lbl = if (range == Range.WEEK) d.dayOfWeek.getDisplayName(TextStyle.SHORT, NL).take(2)
                else if (i % 5 == 4 || i == n - 1) d.dayOfMonth.toString() else ""
                val full = "${d.dayOfWeek.getDisplayName(TextStyle.FULL, NL)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, NL)}"
                Bar(lbl, full, days[d] ?: 0L, d == today)
            }
        }
        Range.YEAR -> (0 until 12).map { i ->
            val m = from.plusMonths(i.toLong())
            val sum = days.filterKeys { it.year == m.year && it.month == m.month }.values.sum()
            Bar(
                m.month.getDisplayName(TextStyle.NARROW, NL).uppercase(),
                "${m.month.getDisplayName(TextStyle.FULL, NL)} ${m.year}",
                sum, m.month == today.month && m.year == today.year
            )
        }
    }
    val total = days.values.sum()
    val first = db.firstDay()
    val start = if (first != null && first.isAfter(from)) first else from
    val nDays = (today.toEpochDay() - start.toEpochDay() + 1).coerceAtLeast(1)
    val ss = db.sessionStats(from, today)
    return Stats(
        todayMs = days[today] ?: 0L,
        bars = bars,
        totalMs = total,
        avgPerDayMs = if (first == null) 0 else total / nDays,
        longestMs = ss.longestMs,
        popups = ss.alerts,
        perApp = db.perApp(from, today),
    )
}

@Composable
fun Overview(resumeTick: Int, onGoSettings: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var range by remember { mutableStateOf(Range.WEEK) }
    var stats by remember { mutableStateOf<Stats?>(null) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val enabled = remember(resumeTick) { isServiceEnabled(ctx) }

    LaunchedEffect(range, resumeTick) {
        selected = null
        while (true) {
            WatchService.instance?.flushNow()
            delay(300)
            stats = withContext(Dispatchers.IO) { loadStats(ctx, range) }
            delay(15_000)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (!enabled) {
            Card(
                Modifier.fillMaxWidth().clickable { onGoSettings() },
                colors = CardDefaults.cardColors(containerColor = Red),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Social Killer staat nog uit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Tik hier om hem aan te zetten, anders wordt er niets bijgehouden.", color = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        val s = stats
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(20.dp)) {
                Text("Vandaag", color = Muted, fontSize = 14.sp)
                Text(
                    if (s == null) "…" else Messages.shortDuration(s.todayMs),
                    fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Ink
                )
                Text(verdict(s?.todayMs ?: 0), color = Muted, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Range.values().forEach { r ->
                FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r.label) })
            }
        }
        Spacer(Modifier.height(8.dp))

        if (s != null) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    val sel = selected?.let { s.bars.getOrNull(it) }
                    Text(
                        if (sel != null) "${sel.full}: ${Messages.shortDuration(sel.ms)}" else "Tik op een staaf voor details",
                        color = if (sel != null) Ink else Muted, fontSize = 14.sp, fontWeight = if (sel != null) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(12.dp))
                    BarChart(s.bars, selected) { selected = if (selected == it) null else it }
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Totaal", Messages.shortDuration(s.totalMs), Modifier.weight(1f))
                StatTile("Gem. per dag", Messages.shortDuration(s.avgPerDayMs), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Langste sessie", Messages.shortDuration(s.longestMs), Modifier.weight(1f))
                StatTile("Pop-ups gehad", s.popups.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))

            Text("Per app", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    if (s.perApp.isEmpty()) {
                        Text("Nog niks gemeten in deze periode. Goed bezig (of hij staat uit).", color = Muted)
                    }
                    val max = s.perApp.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                    s.perApp.forEachIndexed { i, (pkg, ms) ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(SocialApps.label(ctx, pkg), color = Ink, fontWeight = FontWeight.Medium)
                            Text(Messages.shortDuration(ms), color = Muted)
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Soft)) {
                            Box(
                                Modifier.fillMaxWidth((ms.toFloat() / max).coerceIn(0.02f, 1f)).fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp)).background(if (i == 0) Red else Purple)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun verdict(ms: Long): String {
    val m = ms / 60000
    return when {
        m == 0L -> "Nog niks. Wie ben jij en wat heb je met de echte gebruiker gedaan?"
        m < 15 -> "Valt mee. Nog wel."
        m < 45 -> "Het begint ergens op te lijken."
        m < 90 -> "Dat is een hele film. Zonder film."
        m < 180 -> "Je duim verdient een massage."
        else -> "Dit is geen hobby meer, dit is een bijbaan."
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Muted, fontSize = 13.sp)
            Text(value, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BarChart(bars: List<Bar>, selected: Int?, onTap: (Int) -> Unit) {
    val max = bars.maxOfOrNull { it.ms }?.coerceAtLeast(60_000) ?: 60_000
    Row(Modifier.fillMaxWidth()) {
        Text(Messages.shortDuration(max), color = Muted, fontSize = 10.sp, modifier = Modifier.width(44.dp))
    }
    Row(
        Modifier.fillMaxWidth().height(160.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(if (bars.size > 12) 2.dp else 6.dp)
    ) {
        bars.forEachIndexed { i, b ->
            val frac = (b.ms.toFloat() / max).coerceIn(0f, 1f)
            val color = when {
                selected == i -> Ink
                b.highlight -> Red
                else -> Purple
            }
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable { onTap(i) },
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(if (b.ms > 0) frac.coerceAtLeast(0.02f) else 0.01f)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (b.ms > 0) color else Color(0xFFE6E3F0))
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (bars.size > 12) 2.dp else 6.dp)) {
        bars.forEach { b ->
            Text(
                b.label, Modifier.weight(1f), color = Muted, fontSize = 11.sp,
                textAlign = TextAlign.Center, maxLines = 1, softWrap = false
            )
        }
    }
}

// ------------------------------------------------------------------ Instellingen

@SuppressLint("BatteryLife")
@Composable
fun SettingsScreen(resumeTick: Int) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val enabled = remember(resumeTick) { isServiceEnabled(ctx) }
    val battery = remember(resumeTick) { isBatteryFree(ctx) }
    var vibrate by remember { mutableStateOf(prefs.vibrate) }
    var paused by remember { mutableStateOf(prefs.paused) }
    var disabled by remember { mutableStateOf(prefs.disabledKnown) }
    var custom by remember { mutableStateOf(prefs.customPackages) }
    var showPicker by remember { mutableStateOf(false) }

    fun changed() = WatchService.instance?.reloadTracked()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Status
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (enabled) Color(0xFFE7F7EE) else Color(0xFFFDECEC))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    if (enabled) "✅ Social Killer houdt je in de gaten" else "⛔ Social Killer staat uit",
                    fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink
                )
                Spacer(Modifier.height(6.dp))
                if (!enabled) {
                    Text(
                        "Zet hem aan via Toegankelijkheid → Geïnstalleerde apps → Social Killer. " +
                            "Hij kijkt alleen wélke app open staat, niet wat je erin doet.",
                        color = Ink, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Kun je hem niet aanzetten (grijs, 'beperkte instelling')? Ga naar Instellingen → Apps → Social Killer → ⋮ rechtsboven → 'Beperkte instellingen toestaan' en probeer opnieuw.",
                        color = Muted, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Open Toegankelijkheid") }
                    OutlinedButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("Open app-info (voor beperkte instellingen)") }
                } else {
                    Text("Draait op de achtergrond. Je hoeft deze app verder niet open te hebben.", color = Ink, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (!battery) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Batterijbesparing uitzetten", fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        "Samsung zet apps soms 'in slaap'. Zet Social Killer op 'Onbeperkt' zodat hij blijft draaien.",
                        color = Muted, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        try {
                            ctx.startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }) { Text("Niet meer beperken") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        SectionTitle("Gedrag")
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SwitchRow("Even pauze", "Tijdelijk niets bijhouden en geen pop-ups", paused) {
                    paused = it; prefs.paused = it; changed()
                }
                HorizontalDivider()
                SwitchRow("Trillen bij de grote waarschuwing", "Alleen na anderhalf uur. Nooit geluid.", vibrate) {
                    vibrate = it; prefs.vibrate = it
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Elk kwartier een pop-up. Tot 45 min grappig, tot 90 min scherper, daarna een groot scherm dat pas na 5 seconden weg kan. " +
                "Wisselen tussen apps telt gewoon door; pas na 3 minuten zonder social media begint de teller opnieuw.",
            color = Muted, fontSize = 13.sp
        )
        Spacer(Modifier.height(10.dp))
        Text("Probeer de pop-ups:", color = Ink, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "15 min", 2 to "45 min", 3 to "90 min").forEach { (lvl, lbl) ->
                OutlinedButton(onClick = { WatchService.instance?.showTest(lvl) }, enabled = enabled) { Text(lbl) }
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionTitle("Apps die meetellen")
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SocialApps.known.forEachIndexed { i, app ->
                    if (i > 0) HorizontalDivider()
                    val installed = app.packages.any { SocialApps.isInstalled(ctx, it) }
                    SwitchRow(app.name, if (installed) "Op deze telefoon" else "Niet geïnstalleerd", app.name !in disabled) { on ->
                        disabled = if (on) disabled - app.name else disabled + app.name
                        prefs.disabledKnown = disabled; changed()
                    }
                }
                custom.sorted().forEach { pkg ->
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(SocialApps.label(ctx, pkg), color = Ink, fontWeight = FontWeight.Medium)
                            Text("Zelf toegevoegd", color = Muted, fontSize = 13.sp)
                        }
                        TextButton(onClick = {
                            custom = custom - pkg; prefs.customPackages = custom; changed()
                        }) { Text("Verwijder", color = Red) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { showPicker = true }, colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
            Text("+ Andere app toevoegen")
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showPicker) {
        val known = remember { SocialApps.known.flatMap { it.packages }.toSet() }
        val apps = remember { SocialApps.launchableApps(ctx).filter { it.first !in known && it.first !in custom } }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Sluiten") } },
            title = { Text("Welke app ook?") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps, key = { it.first }) { (pkg, label) ->
                        Text(
                            label,
                            Modifier.fillMaxWidth().clickable {
                                custom = custom + pkg; prefs.customPackages = custom; changed(); showPicker = false
                            }.padding(vertical = 12.dp),
                            color = Ink
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(t, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontWeight = FontWeight.Medium)
            Text(sub, color = Muted, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
