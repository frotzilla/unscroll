package com.redwan.unscroll.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redwan.unscroll.data.Catalog
import com.redwan.unscroll.data.Store
import com.redwan.unscroll.data.Usage
import com.redwan.unscroll.service.GuardService
import com.redwan.unscroll.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Packages the user is actively guarding: installed Reels apps plus AntiScroll apps. */
fun trackedPackages(ctx: Context): Set<String> {
    val reels = Catalog.ReelsApp.entries.map { it.pkg }.filter { GuardService.isInstalled(ctx.packageManager, it) }
    return (reels + Store.scrollRules().values.filter { it.active }.map { it.pkg }).toSet()
}

fun share(ctx: Context) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "I am using Unscroll to block Reels and Shorts and to stop doomscrolling. Give it a try.")
    ctx.startActivity(Intent.createChooser(send, "Share Unscroll"))
}

@Composable
fun HomeScreen(nav: Nav) {
    val ctx = LocalContext.current
    var showPause by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Unscroll")
        StatusBanner(nav)

        if (now < Store.pauseUntil) {
            Card(Modifier.padding(horizontal = 12.dp)) {
                val left = (Store.pauseUntil - now) / 1000
                Title("Paused", color = C.warn)
                Body("Protection is off for %d:%02d more. Everything turns back on by itself.".format(left / 60, left % 60))
                Gap()
                PinkButton("Resume now") { Store.endPause() }
            }
            Gap()
        }

        HeroCard()

        Section("Protections")
        Row(Modifier.padding(horizontal = 12.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.weight(1f).fillMaxHeight(), onClick = { nav.tab("reels") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Title("Block Reels", modifier = Modifier.weight(1f))
                    Text(if (Store.reelsMaster) "On" else "Off", color = if (Store.reelsMaster) C.pink else C.muted)
                }
                Gap(10)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Catalog.ReelsApp.entries
                        .filter { Store.reelsApp(it) && GuardService.isInstalled(ctx.packageManager, it.pkg) }
                        .take(6).forEach { AppIcon(it.pkg, 22) }
                }
            }
            if (!Store.hidePause) {
                Card(Modifier.width(110.dp).fillMaxHeight(), onClick = { if (Store.paused) Store.endPause() else showPause = true }) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (Store.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null, tint = C.text, modifier = Modifier.size(30.dp))
                        Text(if (Store.paused) "Resume" else "Pause", color = C.text)
                    }
                }
            }
        }
        Gap(10)
        val watched = Store.scrollRules().values.count { it.active }
        Card(Modifier.padding(horizontal = 12.dp), onClick = { nav.tab("scroll") }) {
            Title("AntiScroll apps")
            Body(if (watched == 0) "Get a popup reminder when you overscroll" else "Watching $watched app${if (watched == 1) "" else "s"} for long scrolling sessions")
        }

        if (!Store.hideBreaks) {
            Section("Breaks")
            Card(Modifier.padding(horizontal = 12.dp), onClick = { nav.go("breaks") }) {
                val breaks = Store.breaks
                Title("Scheduled breaks")
                if (breaks.isEmpty()) {
                    Body("Relax blocking on chosen apps at chosen times, like evenings or weekends.")
                } else {
                    breaks.forEach { b ->
                        val live = Store.activeBreak("*", list = listOf(b.copy(apps = emptySet()))) != null
                        Body("${b.name}: ${breakSummary(b)}${if (live) "  (on now)" else ""}", color = if (live) C.pink else C.muted)
                    }
                }
            }
        }

        Section("Insights")
        InsightsCard(nav)
        Gap(10)
        StreakCard()
        Gap(10)
        Card(Modifier.padding(horizontal = 12.dp), onClick = { nav.go("tips") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title("Your plan", modifier = Modifier.weight(1f))
                Text("${Store.tipsProgress()} of 4", color = C.muted)
            }
            Gap(8)
            Meter(Store.tipsProgress() / 4f)
            Gap(8)
            Body(Store.why.ifBlank { "Write down your why, your rule, and what you will do instead." })
        }

        Section("More")
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.weight(1f), onClick = { share(ctx) }) {
                Icon(Icons.Filled.Share, null, tint = C.text); Gap(8); Title("Share", 15)
            }
            Card(Modifier.weight(1f), onClick = { nav.go("trouble") }) {
                Icon(Icons.Filled.Build, null, tint = C.text); Gap(8); Title("Troubleshooting", 15)
            }
        }
        Gap(24)
    }

    if (showPause) PauseDialog { showPause = false }
}

@Composable
private fun StatusBanner(nav: Nav) {
    val ctx = LocalContext.current
    val enabled = GuardService.isEnabled(ctx)
    if (enabled && GuardService.running) return
    Card(Modifier.padding(horizontal = 12.dp).background(C.card), onClick = { nav.go(if (enabled) "trouble" else "setup") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = C.warn)
            Spacer(Modifier.width(10.dp))
            Title(if (enabled) "Unscroll was stopped" else "Protection is off", color = C.warn)
        }
        Gap(6)
        Body(
            if (enabled) "Android shut the service down in the background. Tap to fix battery restrictions and restart it."
            else "Turn on the Unscroll accessibility service so it can block Reels and watch your scrolling."
        )
    }
    Gap(10)
}

@Composable
private fun HeroCard() {
    val ctx = LocalContext.current
    var perDay by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(Store.rev.intValue) {
        if (Usage.granted(ctx)) {
            perDay = withContext(Dispatchers.Default) {
                val tracked = trackedPackages(ctx)
                val r = Usage.report(ctx, 7)
                r.perDay.sumOf { day -> day.filterKeys { it in tracked }.values.sum() } / 7
            }
        }
    }
    Card(Modifier.padding(horizontal = 12.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (perDay > 0) {
                val days = perDay * 365 / 86_400_000.0
                Text("%.0f days".format(days), color = C.pink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Body("a year at your current pace of ${Usage.fmt(perDay)} a day on your scroll apps. Is it worth it?", color = C.text, modifier = Modifier.padding(top = 4.dp).fillMaxWidth())
            } else {
                Text("38 days", color = C.pink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    "That is what two and a half hours of scrolling a day adds up to in a year. Is it worth it?",
                    color = C.text, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun InsightsCard(nav: Nav) {
    val ctx = LocalContext.current
    var range by remember { mutableIntStateOf(7) }
    var report by remember { mutableStateOf<Usage.Report?>(null) }
    val granted = Usage.granted(ctx)
    LaunchedEffect(range, granted) {
        if (granted) report = withContext(Dispatchers.Default) { Usage.report(ctx, range) }
    }
    Card(Modifier.padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("Screen time", modifier = Modifier.weight(1f))
            listOf(1 to "Today", 7 to "7d", 30 to "30d").forEach { (d, l) ->
                Chip(l, range == d, Modifier.padding(start = 6.dp)) { range = d }
            }
        }
        Gap()
        if (!granted) {
            Body("Grant usage access to see how much time goes to each app.")
            Gap()
            PinkButton("Grant permission") { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        } else {
            val r = report
            if (r == null) Body("Loading...") else {
                val tracked = trackedPackages(ctx)
                val apps = r.perApp.entries.sortedByDescending { it.value }
                val total = apps.sumOf { it.value }
                val trackedTotal = apps.filter { it.key in tracked }.sumOf { it.value }
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(Usage.fmt(total / range), color = C.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Body("daily average, all apps", size = 12)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(Usage.fmt(trackedTotal / range), color = C.pink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Body("daily average, scroll apps", size = 12)
                    }
                }
                Gap()
                apps.take(3).forEach { (pkg, ms) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        AppIcon(pkg, 22)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Body(appLabel(ctx, pkg), color = C.text, size = 13)
                            Meter(if (total > 0) ms.toFloat() / apps.first().value else 0f)
                        }
                        Spacer(Modifier.width(10.dp))
                        Body(Usage.fmt(ms), size = 12)
                    }
                }
            }
        }
        Gap()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Reels blocked", Store.counter(Store.Counter.REELS, range), Modifier.weight(1f))
            Stat("Popups", Store.counter(Store.Counter.POPUPS, range), Modifier.weight(1f))
        }
        Gap(8)
        Text("Details", color = C.pink, fontSize = 14.sp, modifier = Modifier.clickable { nav.go("insights") }.padding(vertical = 6.dp))
    }
}

@Composable
fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier.background(C.card2).padding(12.dp)) {
        Text("$value", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Body(label, size = 12)
    }
}

@Composable
private fun StreakCard() {
    val s = Store.streak()
    Card(Modifier.padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Body("Days without pausing", color = C.text, size = 15, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                Text("${s.current}", color = C.pink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Body("Current", size = 12)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                Text("${s.record}", color = C.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Body("Record", size = 12)
            }
        }
    }
}

@Composable
fun PauseDialog(onDismiss: () -> Unit) {
    var hours by remember { mutableIntStateOf(0) }
    var mins by remember { mutableIntStateOf(30) }
    var typed by remember { mutableStateOf("") }
    val needSentence = Store.antiPause && Store.pausesToday() >= 2
    val sentenceOk = !needSentence || typed.trim().equals(Catalog.PAUSE_SENTENCE, ignoreCase = true)
    fun pause(m: Int) {
        if (!sentenceOk || m <= 0) return
        Store.startPause(m)
        onDismiss()
    }
    SquareDialog(onDismiss) {
        Title("Pause protection", 20)
        Gap(4)
        Body("Pausing resets your streak. Everything turns back on by itself.")
        if (needSentence) {
            Gap()
            Body("You have already paused ${Store.pausesToday()} times today. Type this to continue:", color = C.warn)
            Gap(6)
            Body("\"${Catalog.PAUSE_SENTENCE}\"", color = C.text)
            Gap(6)
            Field(typed, { typed = it }, "Type the sentence exactly")
        }
        Gap()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 5, 15).forEach { m -> Chip("$m min", false, Modifier.weight(1f)) { pause(m) } }
        }
        Gap(16)
        Body("Custom", color = C.text)
        Gap(6)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stepper("$hours h", { hours = (hours - 1).coerceAtLeast(0) }, { hours = (hours + 1).coerceAtMost(12) })
        }
        Gap(6)
        Stepper("$mins min", { mins = (mins - 5).coerceAtLeast(0) }, { mins = (mins + 5).coerceAtMost(55) })
        Gap(16)
        PinkButton("Pause for ${if (hours > 0) "${hours}h " else ""}${mins}m", enabled = sentenceOk && hours * 60 + mins > 0) { pause(hours * 60 + mins) }
        Gap(8)
        GhostButton("Cancel", onClick = onDismiss)
    }
}

fun breakSummary(b: com.redwan.unscroll.data.BreakRule): String {
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val days = when {
        b.days.size == 7 -> "Every day"
        b.days == setOf(6, 7) -> "Weekends"
        b.days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
        else -> b.days.sorted().joinToString(" ") { names[it - 1] }
    }
    fun t(m: Int) = "%d:%02d".format(m / 60, m % 60)
    return "$days ${t(b.startMin)} to ${t(b.endMin)}"
}

fun timeString(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
