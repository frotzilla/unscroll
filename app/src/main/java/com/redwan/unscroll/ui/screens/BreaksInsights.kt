package com.redwan.unscroll.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redwan.unscroll.data.BreakRule
import com.redwan.unscroll.data.Catalog
import com.redwan.unscroll.data.Store
import com.redwan.unscroll.data.Usage
import com.redwan.unscroll.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun BreaksScreen(nav: Nav, onBack: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Scheduled breaks", onBack) {
            Icon(Icons.Filled.Add, "Add break", tint = C.pink, modifier = Modifier.padding(12.dp).clickable { nav.go("break/0") })
        }
        Body("During a break, blocking relaxes for the apps you pick. Everything turns back on when it ends.", modifier = Modifier.padding(horizontal = 16.dp))
        Gap()
        val breaks = Store.breaks
        if (breaks.isEmpty()) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Card {
                    Title("No breaks yet")
                    Body("A break like \"Friday evening, 7 to 10pm, Reels allowed\" makes the rest of the week easier to hold.")
                    Gap()
                    PinkButton("Schedule a break") { nav.go("break/0") }
                }
            }
        }
        breaks.forEach { b ->
            Card(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), onClick = { nav.go("break/${b.id}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Title(b.name)
                        Body(breakSummary(b), size = 13)
                        Body(
                            listOfNotNull(if (b.relaxReels) "AntiReels" else null, if (b.relaxScroll) "AntiScroll" else null).joinToString(" and ") +
                                " off for " + if (b.apps.isEmpty()) "every app" else "${b.apps.size} app${if (b.apps.size == 1) "" else "s"}",
                            size = 12,
                        )
                    }
                    SquareSwitch(b.enabled, { on -> Store.breaks = Store.breaks.map { if (it.id == b.id) it.copy(enabled = on) else it } })
                }
            }
        }
        Gap(24)
    }
}

@Composable
fun BreakEditor(id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val existing = Store.breaks.firstOrNull { it.id == id }
    var b by remember {
        mutableStateOf(existing ?: BreakRule(System.currentTimeMillis(), "Evening break", setOf(5, 6, 7), 19 * 60, 22 * 60, emptySet()))
    }
    var picking by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { launchableApps(ctx) } }

    fun pickTime(start: Boolean) {
        val m = if (start) b.startMin else b.endMin
        TimePickerDialog(ctx, { _, h, min ->
            b = if (start) b.copy(startMin = h * 60 + min) else b.copy(endMin = h * 60 + min)
        }, m / 60, m % 60, android.text.format.DateFormat.is24HourFormat(ctx)).show()
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar(if (existing == null) "New break" else "Edit break", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Field(b.name, { b = b.copy(name = it) }, "Name", singleLine = true)
            Gap(20)
            Title("Days", 16)
            Gap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..7).forEach { d ->
                    val name = java.time.DayOfWeek.of(d).getDisplayName(TextStyle.NARROW, Locale.getDefault())
                    Chip(name, d in b.days, Modifier.weight(1f)) { b = b.copy(days = if (d in b.days) b.days - d else b.days + d) }
                }
            }
            Gap(20)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f).background(C.card).clickable { pickTime(true) }.padding(14.dp)) {
                    Body("Starts", size = 12); Text("%d:%02d".format(b.startMin / 60, b.startMin % 60), color = C.text, fontSize = 24.sp)
                }
                Column(Modifier.weight(1f).background(C.card).clickable { pickTime(false) }.padding(14.dp)) {
                    Body("Ends", size = 12); Text("%d:%02d".format(b.endMin / 60, b.endMin % 60), color = C.text, fontSize = 24.sp)
                }
            }
            if (b.endMin <= b.startMin) Body("Ends the next morning.", size = 12, modifier = Modifier.padding(top = 6.dp))
            Gap(20)
            Title("What relaxes", 16)
            ToggleRow("AntiReels", "Reels and Shorts are allowed", b.relaxReels) { b = b.copy(relaxReels = it) }
            ToggleRow("AntiScroll", "No scroll popups or cooldowns", b.relaxScroll) { b = b.copy(relaxScroll = it) }
            Gap(12)
            Title("Apps", 16)
            Gap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Every app", b.apps.isEmpty(), Modifier.weight(1f)) { b = b.copy(apps = emptySet()) }
                Chip(if (b.apps.isEmpty()) "Choose apps" else "${b.apps.size} chosen", b.apps.isNotEmpty(), Modifier.weight(1f)) { picking = true }
            }
            if (b.apps.isNotEmpty()) {
                Gap(8)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { b.apps.take(10).forEach { AppIcon(it, 26) } }
            }
            Gap(24)
            PinkButton("Save", enabled = b.days.isNotEmpty() && (b.relaxReels || b.relaxScroll)) {
                Store.breaks = if (existing == null) Store.breaks + b else Store.breaks.map { if (it.id == b.id) b else it }
                onBack()
            }
            if (existing != null) {
                Gap(8)
                GhostButton("Delete break") { Store.breaks = Store.breaks.filter { it.id != b.id }; onBack() }
            }
        }
        Gap(24)
    }

    if (picking) {
        // Reels apps and watched apps first, since those are the ones breaks affect
        val relevant = (Catalog.ReelsApp.entries.map { it.pkg } + Store.scrollRules().keys).toSet()
        val sorted = apps.sortedByDescending { it.first in relevant }
        AppPicker(sorted, b.apps, onDismiss = { picking = false }) { b = b.copy(apps = it) }
    }
}

@Composable
fun InsightsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var range by remember { mutableIntStateOf(7) }
    var onlyTracked by remember { mutableStateOf(true) }
    var report by remember { mutableStateOf<Usage.Report?>(null) }
    var picked by remember { mutableIntStateOf(-1) }
    val granted = Usage.granted(ctx)
    LaunchedEffect(range) {
        picked = -1
        if (granted) report = withContext(Dispatchers.Default) { Usage.report(ctx, range) }
    }
    val tracked = remember { trackedPackages(ctx) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Insights", onBack)
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "Today", 7 to "7 days", 30 to "30 days").forEach { (d, l) -> Chip(l, range == d, Modifier.weight(1f)) { range = d } }
        }
        Gap()
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Scroll apps", onlyTracked, Modifier.weight(1f)) { onlyTracked = true }
            Chip("All apps", !onlyTracked, Modifier.weight(1f)) { onlyTracked = false }
        }
        Gap()
        val r = report
        if (!granted) {
            Card(Modifier.padding(horizontal = 12.dp)) {
                Body("Usage access is off, so screen time is not available. The counters below still work.")
            }
        } else if (r != null) {
            fun keep(p: String) = !onlyTracked || p in tracked
            val daily = r.perDay.map { day -> day.filterKeys(::keep).values.sum() }
            Card(Modifier.padding(horizontal = 12.dp)) {
                val total = daily.sum()
                Text(Usage.fmt(total / range), color = C.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Body("daily average over ${if (range == 1) "today" else "$range days"}")
                if (range > 1) {
                    Gap()
                    val sel = picked.takeIf { it in daily.indices }
                    Body(
                        if (sel != null) "${r.days[sel].dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${r.days[sel].dayOfMonth}: ${Usage.fmt(daily[sel])}"
                        else "Tap a bar for that day",
                        color = if (sel != null) C.text else C.muted, size = 13,
                    )
                    Gap(6)
                    DayBars(daily, picked) { picked = it }
                    Row {
                        Body(r.days.first().let { "${it.monthValue}/${it.dayOfMonth}" }, size = 11, modifier = Modifier.weight(1f))
                        Body("Today", size = 11)
                    }
                }
            }
            Section("By app")
            val apps = r.perApp.filterKeys(::keep).entries.sortedByDescending { it.value }.take(15)
            val top = apps.firstOrNull()?.value ?: 1L
            Card(Modifier.padding(horizontal = 12.dp)) {
                if (apps.isEmpty()) Body("No time recorded yet.")
                apps.forEach { (pkg, ms) ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg, 28)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Body(appLabel(ctx, pkg), color = C.text, size = 14, modifier = Modifier.weight(1f))
                                Body(Usage.fmt(ms), size = 13)
                            }
                            Gap(4)
                            Meter(ms.toFloat() / top)
                        }
                    }
                }
            }
        } else {
            Body("Loading...", modifier = Modifier.padding(16.dp))
        }
        Section("What Unscroll did")
        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Reels and Shorts blocked", Store.counter(Store.Counter.REELS, range), Modifier.weight(1f))
                Stat("Scroll popups", Store.counter(Store.Counter.POPUPS, range), Modifier.weight(1f))
            }
            Gap(10)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Sites blocked", Store.counter(Store.Counter.SITES, range), Modifier.weight(1f))
                Stat("Cooldowns", Store.counter(Store.Counter.COOLDOWNS, range), Modifier.weight(1f))
            }
        }
        Gap(24)
    }
}

/** Single series bars, one per day. Tapping a bar selects it; the selected bar stays pink, the rest dim. */
@Composable
private fun DayBars(values: List<Long>, selected: Int, onPick: (Int) -> Unit) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(
        Modifier.fillMaxWidth().height(140.dp).pointerInput(values) {
            detectTapGestures { o -> onPick((o.x / (size.width / values.size)).toInt().coerceIn(0, values.lastIndex)) }
        },
    ) {
        val slot = size.width / values.size
        val gap = 2.dp.toPx().coerceAtLeast(slot * 0.25f)
        values.forEachIndexed { i, v ->
            val h = (v.toFloat() / max) * (size.height - 4.dp.toPx())
            val color = if (selected < 0 || selected == i) C.pink else C.pink.copy(alpha = 0.35f)
            drawRect(color, Offset(i * slot + gap / 2, size.height - h), Size(slot - gap, h))
        }
        drawRect(C.line, Offset(0f, size.height - 1f), Size(size.width, 1f))
    }
}
