package com.redwan.unscroll.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redwan.unscroll.data.BreakRule
import com.redwan.unscroll.data.Catalog
import com.redwan.unscroll.data.Catalog.ReelsApp
import com.redwan.unscroll.data.ScrollRule
import com.redwan.unscroll.data.Store
import com.redwan.unscroll.data.Usage
import com.redwan.unscroll.service.GuardService
import com.redwan.unscroll.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.TextStyle
import java.util.Locale

/** Launchable apps, likely time sinks first, then alphabetical. */
fun launchableApps(ctx: Context): List<Pair<String, String>> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val all = pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.distinct()
        .filter { it != ctx.packageName }
        .map { it to appLabel(ctx, it) }
    val suggested = Catalog.suggestedScrollApps
    return all.sortedWith(compareBy({ suggested.indexOf(it.first).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.second.lowercase() }))
}

private fun installedReelsApps(ctx: Context) =
    ReelsApp.entries.filter { a -> (listOf(a.pkg) + a.altPkgs()).any { GuardService.isInstalled(ctx.packageManager, it) } }

/** One line describing everything switched on for [pkg]. */
private fun summary(pkg: String, rule: ScrollRule?): String {
    val parts = mutableListOf<String>()
    ReelsApp.byPkg(pkg)?.let { if (Store.reelsMaster && Store.reelsApp(it)) parts += "${it.surface} blocked" }
    if (rule != null) {
        if (rule.active) parts += "limit ${rule.limitSec / 60} min"
        if (rule.dailyMin > 0) parts += "${rule.dailyMin} min a day"
        if (rule.askIntent) parts += "asks why"
    }
    return parts.joinToString(", ").replaceFirstChar { it.uppercase() }
}

@Composable
fun AppsScreen(nav: Nav) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { launchableApps(ctx) } }
    val rules = Store.scrollRules()
    val shortApps = remember { installedReelsApps(ctx) }
    val shortPkgs = shortApps.map { it.pkg }.toSet()
    val rest = apps.filter { it.first !in shortPkgs && (query.isBlank() || it.second.contains(query, true)) }

    LazyColumn {
        item { TopBar("Apps") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ToggleRow("Block short videos", "Master switch for Reels, Shorts, and similar feeds", Store.reelsMaster) { Store.reelsMaster = it }
            }
            Divider()
            NavRow("Lockout", if (Store.cooldownOn) "${Store.cooldownMin} min after any scroll limit" else "Off") { nav.go("lockout") }
            NavRow("Free time", if (Store.breaks.isEmpty()) "None scheduled" else "${Store.breaks.size} window${if (Store.breaks.size == 1) "" else "s"}") { nav.go("free") }
            Divider()
            Section("Short video apps")
        }
        items(shortApps.filter { query.isBlank() || it.label.contains(query, true) }, key = { "s" + it.pkg }) { a ->
            AppRow(a.pkg, a.label, summary(a.pkg, rules[a.pkg])) { nav.go("app/${a.pkg}") }
        }
        item {
            Section("Every other app")
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { Field(query, { query = it }, "Search apps", singleLine = true) }
        }
        if (apps.isEmpty()) item { Body("Loading apps...", modifier = Modifier.padding(16.dp)) }
        items(rest, key = { it.first }) { (pkg, label) ->
            AppRow(pkg, label, summary(pkg, rules[pkg])) { nav.go("app/$pkg") }
        }
        item { Gap(24) }
    }
}

@Composable
private fun AppRow(pkg: String, label: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(pkg, 36)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = C.text, fontSize = 16.sp)
            if (sub.isNotEmpty()) Text(sub, color = C.pink, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.muted)
    }
}

@Composable
fun AppPicker(apps: List<Pair<String, String>>, selected: Set<String>, onDismiss: () -> Unit, onChange: (Set<String>) -> Unit) {
    var q by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(selected) }
    SquareDialog(onDismiss) {
        Title("Choose apps", 18)
        Gap(8)
        Field(q, { q = it }, "Search", singleLine = true)
        Gap(8)
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            items(apps.filter { q.isBlank() || it.second.contains(q, true) }, key = { it.first }) { (pkg, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { sel = if (pkg in sel) sel - pkg else sel + pkg }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(pkg, 28); Spacer(Modifier.width(10.dp))
                    Text(label, color = C.text, modifier = Modifier.weight(1f))
                    SquareCheck(pkg in sel) { sel = if (it) sel + pkg else sel - pkg }
                }
            }
        }
        Gap()
        PinkButton("Done") { onChange(sel); onDismiss() }
    }
}

// Per app page

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Section(title)
    Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(Round).background(C.card).padding(horizontal = 16.dp, vertical = 4.dp)) { content() }
}

@Composable
private fun Opt(key: String, title: String, sub: String? = null, def: Boolean = false, badge: String? = null, enabled: Boolean = true) =
    ToggleRow(title, sub, Store.opt(key, def), enabled = enabled, badge = badge) { Store.setOpt(key, it) }

/** A row showing the current choice; tapping opens a list to pick from. */
@Composable
private fun Picker(title: String, value: String, options: List<Pair<String, String>>, enabled: Boolean = true, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { open = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Title(title, 16, color = if (enabled) C.text else C.muted, modifier = Modifier.weight(1f))
        Text(options.firstOrNull { it.first == value }?.second ?: value, color = if (enabled) C.pink else C.muted, fontSize = 15.sp)
    }
    if (open) SquareDialog({ open = false }) {
        Title(title, 18)
        Gap()
        options.forEach { (v, l) ->
            Row(Modifier.fillMaxWidth().clickable { onPick(v); open = false }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SquareCheck(v == value) { onPick(v); open = false }
                Spacer(Modifier.width(12.dp))
                Text(l, color = C.text, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun AppScreen(pkg: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val reelsApp = ReelsApp.byPkg(pkg)
    val rule = Store.scrollRule(pkg)
    fun put(r: ScrollRule) = Store.putScrollRule(r)
    var advanced by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar(appLabel(ctx, pkg), onBack) { AppIcon(pkg, 30); Spacer(Modifier.width(12.dp)) }

        if (reelsApp != null) {
            val on = Store.reelsApp(reelsApp)
            Group("Short videos") {
                ToggleRow("Block ${reelsApp.surface}", if (Store.reelsMaster) null else "The master switch in Apps is off", on, enabled = Store.reelsMaster) {
                    Store.setReelsApp(reelsApp, it)
                }
                if (on && Store.reelsMaster) ShortVideoOptions(reelsApp)
            }
        }

        Group("Scroll limit") {
            ToggleRow("Check in after nonstop scrolling", "A full screen pause that asks if you want to keep going", rule.on) { put(rule.copy(on = it)) }
            if (rule.on) {
                Gap(6)
                Body("Nonstop for", color = C.text)
                Gap(6)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ScrollRule.limitPresets.forEach { m -> Chip("$m min", rule.limitSec == m * 60, Modifier.weight(1f)) { put(rule.copy(limitSec = m * 60)) } }
                }
                Gap(8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Body("Exactly", modifier = Modifier.weight(1f))
                    Stepper(if (rule.limitSec >= 60) "${rule.limitSec / 60}m ${rule.limitSec % 60}s" else "${rule.limitSec}s",
                        { put(rule.copy(limitSec = (rule.limitSec - 30).coerceAtLeast(30))) },
                        { put(rule.copy(limitSec = (rule.limitSec + 30).coerceAtMost(4 * 3600))) })
                }
                Gap(14)
                Body("Wait before Continue unlocks", color = C.text)
                Gap(6)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScrollRule.waitPresets.forEach { s -> Chip("${s}s", rule.waitSec == s, Modifier.weight(1f)) { put(rule.copy(waitSec = s)) } }
                }
                Gap(8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Body("Exactly", modifier = Modifier.weight(1f))
                    Stepper("${rule.waitSec}s", { put(rule.copy(waitSec = (rule.waitSec - 5).coerceAtLeast(0))) }, { put(rule.copy(waitSec = (rule.waitSec + 5).coerceAtMost(600))) })
                }
                Gap(10)
                Text(if (advanced) "Hide sensitivity" else "Sensitivity", color = C.pink, fontSize = 14.sp,
                    modifier = Modifier.clickable { advanced = !advanced }.padding(vertical = 8.dp))
                if (advanced) {
                    Body("Scrolling counts as nonstop while you swipe at least ${rule.minScrolls} time${if (rule.minScrolls == 1) "" else "s"} every ${rule.windowSec} seconds.", size = 13)
                    Gap(8)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Body("Swipes", modifier = Modifier.weight(1f))
                        Stepper("${rule.minScrolls}", { put(rule.copy(minScrolls = (rule.minScrolls - 1).coerceAtLeast(1))) }, { put(rule.copy(minScrolls = (rule.minScrolls + 1).coerceAtMost(30))) })
                    }
                    Gap(6)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Body("Every", modifier = Modifier.weight(1f))
                        Stepper("${rule.windowSec}s", { put(rule.copy(windowSec = (rule.windowSec - 5).coerceAtLeast(5))) }, { put(rule.copy(windowSec = (rule.windowSec + 5).coerceAtMost(300))) })
                    }
                    Gap(6)
                }
            }
        }

        Group("Daily allowance") {
            ToggleRow("Limit time per day", "When it runs out, the app stays locked until midnight", rule.dailyMin > 0) {
                put(rule.copy(dailyMin = if (it) 30 else 0))
            }
            if (rule.dailyMin > 0) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Body("Minutes a day", color = C.text, modifier = Modifier.weight(1f))
                    Stepper("${rule.dailyMin} min", { put(rule.copy(dailyMin = (rule.dailyMin - 5).coerceAtLeast(5))) }, { put(rule.copy(dailyMin = (rule.dailyMin + 5).coerceAtMost(600))) })
                }
                Body("Used today: ${Usage.fmt(Store.usedToday(pkg))}", size = 13, modifier = Modifier.padding(bottom = 8.dp))
            }
        }

        Group("Open with intent") {
            ToggleRow("Ask what I am opening it for", "Pick messages, posting, or 5 minutes of scrolling before it opens", rule.askIntent) {
                put(rule.copy(askIntent = it))
            }
        }
        Gap(24)
    }
}

@Composable
private fun ShortVideoOptions(app: ReelsApp) {
    when (app) {
        ReelsApp.INSTAGRAM -> {
            Opt("ig_allow_dm", "Let through reels friends send", "Opens the one reel from a chat. Swiping onward is blocked.")
            Opt("ig_hide_home_reels", "Cover reels in the home feed", "Switch off if a box ever gets stuck on screen.", def = true)
            val feed = if (!Store.opt("ig_block_feed")) "off" else Store.choice("ig_feed_mode", "hide")
            Picker("Home feed", feed, listOf("off" to "Leave it", "hide" to "Cover it, keep Stories", "dms" to "Skip to messages")) { v ->
                Store.setOpt("ig_block_feed", v != "off")
                if (v != "off") Store.setChoice("ig_feed_mode", v)
            }
            Opt("ig_block_stories", "Close Stories when they open")
            Opt("ig_block_comments", "Close comment sheets")
            Opt("ig_hide_explore", "Cover the Explore grid", "Searching still works")
        }
        ReelsApp.FACEBOOK -> {
            Picker("Reels tab sends you to", Store.choice("fb_redirect", "home"),
                listOf("home" to "Home", "back" to "Where you were", "close" to "Out of Facebook")) { Store.setChoice("fb_redirect", it) }
            Opt("fb_allow_msg", "Let through reels sent in Messenger", "The one reel plays. The next one is blocked.")
            val feed = if (!Store.opt("fb_block_feed")) "off" else Store.choice("fb_feed_target", "profile")
            Picker("Home feed", feed, listOf("off" to "Leave it", "profile" to "Open Profile instead", "notifications" to "Open Notifications instead", "friends" to "Open Friends instead", "menu" to "Open Menu instead")) { v ->
                Store.setOpt("fb_block_feed", v != "off")
                if (v != "off") Store.setChoice("fb_feed_target", v)
            }
        }
        ReelsApp.YOUTUBE -> {
            Opt("yt_allow_link", "Let through Shorts from links", "A Short someone sends plays once. The next one is blocked.")
            Opt("yt_hide_shelf", "Cover Shorts rows", "In Home, Search, and Subscriptions", badge = "EXPERIMENTAL")
        }
        ReelsApp.TIKTOK -> Opt("tt_allow_dm", "Let through videos sent in chats", "The For You feed stays blocked")
        else -> {}
    }
}

// Lockout

@Composable
fun LockoutScreen(nav: Nav, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { launchableApps(ctx) } }
    val limited = Store.scrollRules().values.filter { it.active }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Lockout", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Body("When any scroll limit is hit, every limited app locks for a while, so you cannot just switch apps.", color = C.text)
            Gap()
            ToggleRow("Lock all limited apps after a check in", checked = Store.cooldownOn) { Store.cooldownOn = it }
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Body("Lock for", color = C.text, modifier = Modifier.weight(1f))
                Stepper("${Store.cooldownMin} min", { Store.cooldownMin = (Store.cooldownMin - 5).coerceAtLeast(5) }, { Store.cooldownMin = (Store.cooldownMin + 5).coerceAtMost(180) })
            }
        }
        Section("Apps with a scroll limit (${limited.size})")
        if (limited.isEmpty()) Body("None yet. Add a scroll limit from any app's page.", modifier = Modifier.padding(horizontal = 16.dp))
        limited.forEach { r -> AppRow(r.pkg, appLabel(ctx, r.pkg), "") { nav.go("app/${r.pkg}") } }
        Section("Also lock")
        Body("These have no limit of their own but lock along with the rest. Browsers are a good idea.", modifier = Modifier.padding(horizontal = 16.dp))
        Store.cooldownExtraApps.forEach { pkg ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, 30); Spacer(Modifier.width(12.dp))
                Body(appLabel(ctx, pkg), color = C.text, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, "Remove", tint = C.muted, modifier = Modifier.clickable { Store.cooldownExtraApps = Store.cooldownExtraApps - pkg })
            }
        }
        Row(Modifier.clickable { picking = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, null, tint = C.pink); Spacer(Modifier.width(8.dp)); Text("Add apps", color = C.pink)
        }
        Gap(24)
    }
    if (picking) AppPicker(apps, Store.cooldownExtraApps, onDismiss = { picking = false }) { Store.cooldownExtraApps = it }
}

// Free time

@Composable
fun FreeTimeScreen(nav: Nav, onBack: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Free time", onBack) {
            Icon(Icons.Filled.Add, "Add", tint = C.pink, modifier = Modifier.padding(12.dp).clickable { nav.go("free/0") })
        }
        Body("Times when the rules relax, for every app or just the ones you pick.", modifier = Modifier.padding(horizontal = 16.dp))
        Gap()
        val list = Store.breaks
        if (list.isEmpty()) Column(Modifier.padding(horizontal = 16.dp)) { PinkButton("Add a window") { nav.go("free/0") } }
        list.forEach { b ->
            Row(Modifier.fillMaxWidth().clickable { nav.go("free/${b.id}") }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Title(b.name, 16)
                    Body(freeTimeSummary(b), size = 13)
                    Body(
                        listOfNotNull(if (b.relaxReels) "short videos" else null, if (b.relaxScroll) "limits" else null).joinToString(" and ") +
                            " relaxed for " + if (b.apps.isEmpty()) "every app" else "${b.apps.size} app${if (b.apps.size == 1) "" else "s"}",
                        size = 12,
                    )
                }
                SquareSwitch(b.enabled, { on -> Store.breaks = Store.breaks.map { if (it.id == b.id) it.copy(enabled = on) else it } })
            }
            Divider()
        }
        Gap(24)
    }
}

@Composable
fun FreeTimeEditor(id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val existing = Store.breaks.firstOrNull { it.id == id }
    var b by remember { mutableStateOf(existing ?: BreakRule(System.currentTimeMillis(), "Friday night", setOf(5), 20 * 60, 22 * 60, emptySet())) }
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
        TopBar(if (existing == null) "New window" else "Edit window", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Field(b.name, { b = b.copy(name = it) }, "Name", singleLine = true)
            Gap(20)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..7).forEach { d ->
                    val name = java.time.DayOfWeek.of(d).getDisplayName(TextStyle.NARROW, Locale.getDefault())
                    Chip(name, d in b.days, Modifier.weight(1f)) { b = b.copy(days = if (d in b.days) b.days - d else b.days + d) }
                }
            }
            Gap(20)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f).clip(Round).background(C.card).clickable { pickTime(true) }.padding(14.dp)) {
                    Body("From", size = 12); Text("%d:%02d".format(b.startMin / 60, b.startMin % 60), color = C.text, fontSize = 24.sp)
                }
                Column(Modifier.weight(1f).clip(Round).background(C.card).clickable { pickTime(false) }.padding(14.dp)) {
                    Body("Until", size = 12); Text("%d:%02d".format(b.endMin / 60, b.endMin % 60), color = C.text, fontSize = 24.sp)
                }
            }
            if (b.endMin <= b.startMin) Body("Runs past midnight into the next day.", size = 12, modifier = Modifier.padding(top = 6.dp))
            Gap(16)
            ToggleRow("Allow short videos", checked = b.relaxReels) { b = b.copy(relaxReels = it) }
            ToggleRow("Pause limits, allowances, and intent questions", checked = b.relaxScroll) { b = b.copy(relaxScroll = it) }
            Gap(10)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Every app", b.apps.isEmpty(), Modifier.weight(1f)) { b = b.copy(apps = emptySet()) }
                Chip(if (b.apps.isEmpty()) "Some apps" else "${b.apps.size} apps", b.apps.isNotEmpty(), Modifier.weight(1f)) { picking = true }
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
                GhostButton("Delete") { Store.breaks = Store.breaks.filter { it.id != b.id }; onBack() }
            }
        }
        Gap(24)
    }
    if (picking) {
        val relevant = (ReelsApp.entries.map { it.pkg } + Store.scrollRules().keys).toSet()
        AppPicker(apps.sortedByDescending { it.first in relevant }, b.apps, onDismiss = { picking = false }) { b = b.copy(apps = it) }
    }
}
