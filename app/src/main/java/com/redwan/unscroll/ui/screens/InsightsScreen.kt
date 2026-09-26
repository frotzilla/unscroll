package com.redwan.unscroll.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redwan.unscroll.data.Store
import com.redwan.unscroll.data.Usage
import com.redwan.unscroll.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InsightsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var range by remember { mutableIntStateOf(7) }
    var watchedOnly by remember { mutableStateOf(true) }
    var report by remember { mutableStateOf<Usage.Report?>(null) }
    var picked by remember { mutableIntStateOf(-1) }
    val granted = Usage.granted(ctx)
    LaunchedEffect(range) {
        picked = -1
        if (granted) report = withContext(Dispatchers.Default) { Usage.report(ctx, range) }
    }
    val watched = remember { watchedPackages(ctx) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Insights", onBack)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "Today", 7 to "Week", 30 to "Month").forEach { (d, l) -> Chip(l, range == d, Modifier.weight(1f)) { range = d } }
        }
        Gap(8)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Watched apps", watchedOnly, Modifier.weight(1f)) { watchedOnly = true }
            Chip("Everything", !watchedOnly, Modifier.weight(1f)) { watchedOnly = false }
        }
        val r = report
        if (!granted) {
            Body("Usage access is off, so there is no screen time to show. The counts below still work.", modifier = Modifier.padding(16.dp))
        } else if (r == null) {
            Body("Loading...", modifier = Modifier.padding(16.dp))
        } else {
            fun keep(p: String) = !watchedOnly || p in watched
            val daily = r.perDay.map { d -> d.filterKeys(::keep).values.sum() }
            Column(Modifier.padding(16.dp)) {
                Text(Usage.fmt(daily.sum() / range), color = C.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Body("a day on average", size = 13)
                if (range > 1) {
                    Gap(14)
                    val sel = picked.takeIf { it in daily.indices }
                    Body(
                        if (sel != null) "${r.days[sel].dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${r.days[sel].dayOfMonth}: ${Usage.fmt(daily[sel])}" else "Tap a day",
                        color = if (sel != null) C.text else C.muted, size = 13,
                    )
                    Gap(6)
                    DayBars(daily, picked) { picked = it }
                }
            }
            Divider()
            Section("Where it went")
            val apps = r.perApp.filterKeys(::keep).entries.sortedByDescending { it.value }.take(12)
            val top = apps.firstOrNull()?.value ?: 1L
            apps.forEach { (pkg, ms) ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(pkg, 28)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Body(appLabel(ctx, pkg), color = C.text, modifier = Modifier.weight(1f))
                            Body(Usage.fmt(ms), size = 13)
                        }
                        Gap(4)
                        Meter(ms.toFloat() / top)
                    }
                }
            }
        }
        Divider()
        Section("What Unscroll did")
        val cells = listOf(
            "Short videos blocked" to Store.counter(Store.Counter.REELS, range).toString(),
            "Check ins" to Store.counter(Store.Counter.POPUPS, range).toString(),
            "Opens you changed your mind on" to Store.counter(Store.Counter.RECONSIDERED, range).toString(),
            "Focus sessions" to Store.counter(Store.Counter.FOCUS, range).toString(),
            "Lockouts" to Store.counter(Store.Counter.COOLDOWNS, range).toString(),
            "Sites blocked" to Store.counter(Store.Counter.SITES, range).toString(),
            "Distance scrolled" to (Store.counter(Store.Counter.SCROLL_CM, range) / 100).let { if (it >= 1000) "%.1f km".format(it / 1000f) else "$it m" },
        )
        cells.chunked(2).forEach { row ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f).clip(Round).background(C.card).padding(12.dp)) {
                        Text(value, color = C.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Body(label, size = 12)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Gap(24)
    }
}

/** Single series bars, one per day. Tapping selects a day; the others dim. */
@Composable
private fun DayBars(values: List<Long>, selected: Int, onPick: (Int) -> Unit) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(
        Modifier.fillMaxWidth().height(130.dp).pointerInput(values) {
            detectTapGestures { o -> onPick((o.x / (size.width / values.size)).toInt().coerceIn(0, values.lastIndex)) }
        },
    ) {
        val slot = size.width / values.size
        val gap = 2.dp.toPx().coerceAtLeast(slot * 0.3f)
        values.forEachIndexed { i, v ->
            val h = (v.toFloat() / max) * (size.height - 4.dp.toPx())
            val color = if (selected < 0 || selected == i) C.pink else C.pink.copy(alpha = 0.3f)
            drawRect(color, Offset(i * slot + gap / 2, size.height - h), Size(slot - gap, h))
        }
        drawRect(C.line, Offset(0f, size.height - 1f), Size(size.width, 1f))
    }
}
