package com.redwan.unscroll.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redwan.unscroll.data.BreakRule
import com.redwan.unscroll.data.Catalog
import com.redwan.unscroll.data.Store
import com.redwan.unscroll.data.Usage
import com.redwan.unscroll.service.GuardService
import com.redwan.unscroll.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Packages Unscroll is actively guarding: installed short video apps plus apps with their own rules. */
fun watchedPackages(ctx: Context): Set<String> {
    val reels = Catalog.ReelsApp.entries.map { it.pkg }.filter { GuardService.isInstalled(ctx.packageManager, it) }
    return (reels + Store.scrollRules().values.filter { it.any }.map { it.pkg }).toSet()
}

fun share(ctx: Context) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "Unscroll blocks Reels and Shorts and nudges me off the feed. Might help you too.")
    ctx.startActivity(Intent.createChooser(send, "Share Unscroll"))
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun TodayScreen(nav: Nav) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    var snoozeOpen by remember { mutableStateOf(false) }
    var focusAsk by remember { mutableIntStateOf(0) }
    var endFocus by remember { mutableStateOf(false) }

    val enabled = GuardService.isEnabled(ctx)
    val snoozed = now < Store.pauseUntil
    val focusing = now < Store.focusUntil
    val status = when {
        !enabled -> "Off" to C.warn
        !GuardService.running -> "Stopped" to C.warn
        focusing -> "Focus ${clock(Store.focusUntil - now)}" to C.pink
        snoozed && Store.pausedIndefinitely -> "Turned off" to C.warn
        snoozed -> "Snoozed ${clock(Store.pauseUntil - now)}" to C.warn
        else -> "Guarding" to C.pink
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Unscroll", color = C.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(status.second))
            Spacer(Modifier.width(8.dp))
            Text(status.first, color = status.second, fontSize = 14.sp)
        }

        if (!enabled || !GuardService.running) {
            Banner(
                if (enabled) "Android stopped Unscroll in the background. Tap to fix it." else "Protection is not switched on yet. Tap to finish setup.",
            ) { nav.go(if (enabled) "trouble" else "setup") }
        }
        if (snoozed) {
            if (Store.pausedIndefinitely) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(Round).background(C.warn.copy(alpha = 0.16f)).padding(16.dp)) {
                    Title("Unscroll is turned off")
                    Body("Nothing is being blocked or limited until you turn it back on.", color = C.text, size = 13)
                    Gap()
                    PinkButton("Turn back on") { Store.endPause() }
                }
            } else {
                Banner("Snoozed. Everything switches back on in ${clock(Store.pauseUntil - now)}. Tap to resume now.") { Store.endPause() }
            }
        }

        if (focusing) {
            Column(Modifier.fillMaxWidth().padding(16.dp).clip(Round).background(C.pink).padding(20.dp)) {
                Text("FOCUS SESSION", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(clock(Store.focusUntil - now), color = Color.Black, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text("Every watched app is locked.", color = Color.Black, fontSize = 14.sp)
                Gap()
                Text("End early", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { endFocus = true }.padding(vertical = 6.dp))
            }
        }

        TimeBlock(nav)
        Divider()
        StatsRow()
        Divider()

        if (!focusing) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
                Title("Start a focus session", 16)
                Body("Locks every watched app until the timer ends.", size = 13)
                Gap(10)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 50, 90).forEach { m -> Chip("$m min", false, Modifier.weight(1f)) { focusAsk = m } }
                }
            }
            Divider()
        }

        FreeTimeLine(nav)
        Divider()

        if (!Store.hidePause && !snoozed) {
            Column(Modifier.padding(16.dp)) {
                GhostButton("Snooze protection") { snoozeOpen = true }
            }
        }
        Gap(24)
    }

    if (snoozeOpen) SnoozeDialog { snoozeOpen = false }
    if (focusAsk > 0) SquareDialog({ focusAsk = 0 }) {
        Title("Focus for $focusAsk minutes?", 19)
        Gap(6)
        Body("Watched apps stay locked until it ends. Snoozing does not end it.")
        Gap()
        PinkButton("Start") {
            Store.focusUntil = System.currentTimeMillis() + focusAsk * 60_000L
            Store.bump(Store.Counter.FOCUS)
            focusAsk = 0
        }
        Gap(8)
        GhostButton("Cancel") { focusAsk = 0 }
    }
    if (endFocus) SquareDialog({ endFocus = false }) {
        Title("End the session early?", 19)
        Gap(6)
        Body("${clock(Store.focusUntil - now)} left.")
        Gap()
        PinkButton("Keep going") { endFocus = false }
        Gap(8)
        GhostButton("End it") { Store.focusUntil = 0; endFocus = false }
    }
}

@Composable
private fun Banner(text: String, onClick: () -> Unit) {
    Body(
        text, color = C.text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(Round)
            .background(C.warn.copy(alpha = 0.16f)).clickable(onClick = onClick).padding(14.dp),
    )
}

@Composable
private fun TimeBlock(nav: Nav) {
    val ctx = LocalContext.current
    val granted = Usage.granted(ctx)
    var week by remember { mutableStateOf<List<Long>?>(null) }
    LaunchedEffect(granted) {
        if (granted) week = withContext(Dispatchers.Default) {
            val watched = watchedPackages(ctx)
            Usage.report(ctx, 7).perDay.map { d -> d.filterKeys { it in watched }.values.sum() }
        }
    }
    Column(Modifier.fillMaxWidth().clickable { nav.go("insights") }.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text("TODAY IN WATCHED APPS", color = C.muted, fontSize = 12.sp, letterSpacing = 1.sp)
        if (!granted) {
            Gap(6)
            Body("Allow usage access to see your time here.", color = C.text)
            Gap(10)
            PinkButton("Allow usage access") { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            return@Column
        }
        val w = week
        Text(if (w == null) "..." else Usage.fmt(w.last()), color = C.text, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        if (w != null) {
            val avg = w.dropLast(1).average().toLong()
            Body("Last six days averaged ${Usage.fmt(avg)}", size = 13)
            Gap(12)
            WeekBars(w)
        }
    }
}

/** Seven thin bars, today in pink and earlier days dimmed, with a baseline. */
@Composable
private fun WeekBars(values: List<Long>) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        val slot = size.width / values.size
        val barW = slot * 0.5f
        values.forEachIndexed { i, v ->
            val h = (v.toFloat() / max) * (size.height - 2f)
            val c = if (i == values.lastIndex) C.pink else C.muted.copy(alpha = 0.45f)
            drawRect(c, Offset(i * slot + (slot - barW) / 2, size.height - h), Size(barW, h))
        }
        drawRect(C.line, Offset(0f, size.height - 1f), Size(size.width, 1f))
    }
}

@Composable
private fun StatsRow() {
    val metres = Store.counter(Store.Counter.SCROLL_CM, 1) / 100
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        MiniStat("${Store.counter(Store.Counter.REELS, 1)}", "blocked", Modifier.weight(1f))
        MiniStat("${Store.counter(Store.Counter.POPUPS, 1)}", "check ins", Modifier.weight(1f))
        MiniStat("${Store.streak().current}", "clean days", Modifier.weight(1f))
        MiniStat(if (metres >= 1000) "%.1fkm".format(metres / 1000f) else "${metres}m", "scrolled", Modifier.weight(1f))
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = C.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = C.muted, fontSize = 12.sp)
    }
}

@Composable
private fun FreeTimeLine(nav: Nav) {
    val breaks = Store.breaks.filter { it.enabled }
    val live = breaks.firstOrNull { Store.activeBreak("*", list = listOf(it.copy(apps = emptySet()))) != null }
    NavRow(
        "Free time",
        when {
            live != null -> "On now: ${live.name}"
            breaks.isEmpty() -> "Times when the rules relax"
            else -> breaks.joinToString(", ") { it.name }
        },
    ) { nav.go("free") }
}

@Composable
fun SnoozeDialog(onDismiss: () -> Unit) {
    var custom by remember { mutableIntStateOf(20) }
    var typed by remember { mutableStateOf("") }
    val needSentence = Store.antiPause && Store.pausesToday() >= Store.snoozeFreeCount
    val ok = !needSentence || typed.trim().equals(Catalog.PAUSE_SENTENCE, ignoreCase = true)
    var confirmOff by remember { mutableStateOf(false) }
    fun snooze(m: Int) {
        if (!ok || m <= 0) return
        Store.startPause(m)
        onDismiss()
    }
    if (confirmOff) {
        SquareDialog(onDismiss) {
            Title("Turn Unscroll off?", 20)
            Gap(6)
            Body("Short video blocking, scroll limits, allowances, lockouts, and the web filter stay off until you turn them back on yourself. Nothing switches back on automatically.")
            Gap(8)
            Body("Focus sessions and the uninstall guard keep running. Your clean days reset.", size = 13)
            Gap()
            PinkButton("Keep it on", onClick = onDismiss)
            Gap(8)
            GhostButton("Turn off") {
                Store.pauseIndefinitely()
                onDismiss()
            }
        }
        return
    }
    SquareDialog(onDismiss) {
        Title("Snooze protection", 20)
        Gap(4)
        Body("Short video blocking, check ins, and lockouts switch off, then back on by themselves. Snoozing resets your clean days.")
        if (needSentence) {
            Gap()
            Body("That would be snooze number ${Store.pausesToday() + 1} today. Type this first:", color = C.warn)
            Gap(6)
            Body(Catalog.PAUSE_SENTENCE, color = C.text)
            Gap(6)
            Field(typed, { typed = it }, "Type it exactly")
        }
        Gap()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 15, 30).forEach { m -> Chip("$m min", false, Modifier.weight(1f)) { snooze(m) } }
        }
        Gap()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Body("Other", color = C.text, modifier = Modifier.weight(1f))
            Stepper(if (custom >= 60) "${custom / 60}h ${custom % 60}m" else "$custom min",
                { custom = (custom - 5).coerceAtLeast(5) }, { custom = (custom + 5).coerceAtMost(240) })
        }
        Gap()
        PinkButton("Snooze", enabled = ok) { snooze(custom) }
        Gap(8)
        GhostButton("Cancel", onClick = onDismiss)
        Gap(14)
        Text(
            "Turn off completely", color = if (ok) C.warn else C.muted, fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(Round).clickable(enabled = ok) { confirmOff = true }.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

fun freeTimeSummary(b: BreakRule): String {
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val days = when {
        b.days.size == 7 -> "Daily"
        b.days == setOf(6, 7) -> "Sat and Sun"
        b.days == setOf(1, 2, 3, 4, 5) -> "Mon to Fri"
        else -> b.days.sorted().joinToString(", ") { names[it - 1] }
    }
    fun t(m: Int) = "%d:%02d".format(m / 60, m % 60)
    return "$days, ${t(b.startMin)} to ${t(b.endMin)}"
}
