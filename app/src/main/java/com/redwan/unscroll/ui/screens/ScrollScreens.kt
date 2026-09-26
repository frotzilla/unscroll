package com.redwan.unscroll.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redwan.unscroll.data.Catalog
import com.redwan.unscroll.data.ScrollMode
import com.redwan.unscroll.data.ScrollRule
import com.redwan.unscroll.data.Store
import com.redwan.unscroll.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Launchable apps, suggested scroll apps first, then alphabetical. */
fun launchableApps(ctx: Context): List<Pair<String, String>> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val all = pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.distinct()
        .filter { it != ctx.packageName }
        .map { it to appLabel(ctx, it) }
    val suggested = Catalog.suggestedScrollApps
    return all.sortedWith(compareBy({ suggested.indexOf(it.first).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.second.lowercase() }))
}

@Composable
fun ScrollListScreen(nav: Nav) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var extrasOpen by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { launchableApps(ctx) } }
    val rules = Store.scrollRules()
    val shown = apps.filter { query.isBlank() || it.second.contains(query, true) }

    LazyColumn {
        item { TopBar("AntiScroll") }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Field(query, { query = it }, "Search apps", singleLine = true)
                Gap()
                Card {
                    ToggleRow(
                        "Cross app cooldown",
                        "When you hit a scroll limit, your other scroll apps lock too, so you cannot just switch apps.",
                        Store.cooldownOn,
                    ) { Store.cooldownOn = it }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Body("Cooldown length", color = C.text, modifier = Modifier.weight(1f))
                        Stepper("${Store.cooldownMin} min", { Store.cooldownMin = (Store.cooldownMin - 5).coerceAtLeast(5) }, { Store.cooldownMin = (Store.cooldownMin + 5).coerceAtMost(120) })
                    }
                    Divider()
                    Row(Modifier.fillMaxWidth().clickable { extrasOpen = !extrasOpen }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Body("Additional apps to lock (${Store.cooldownExtraApps.size})", color = C.text, modifier = Modifier.weight(1f))
                        Icon(if (extrasOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = C.muted)
                    }
                    if (extrasOpen) {
                        Body("These are not watched for scrolling, but they lock during a cooldown. Browsers are a good pick.", size = 12)
                        Store.cooldownExtraApps.forEach { pkg ->
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(pkg, 26); Spacer(Modifier.width(10.dp))
                                Body(appLabel(ctx, pkg), color = C.text, modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.Close, "Remove", tint = C.muted, modifier = Modifier.clickable { Store.cooldownExtraApps = Store.cooldownExtraApps - pkg })
                            }
                        }
                        Row(Modifier.clickable { picking = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, null, tint = C.pink); Spacer(Modifier.width(8.dp)); Text("Add app", color = C.pink)
                        }
                    }
                }
                Gap()
            }
        }
        if (apps.isEmpty()) item { Body("Loading apps...", modifier = Modifier.padding(16.dp)) }
        items(shown, key = { it.first }) { (pkg, label) ->
            val rule = rules[pkg]
            Row(
                Modifier.fillMaxWidth().clickable { nav.go("app/$pkg") }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(pkg, 36)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, color = C.text, fontSize = 16.sp)
                    if (rule?.active == true) Text(rule.mode.label, color = C.pink, fontSize = 12.sp)
                }
                SquareCheck(rule?.active == true) { on ->
                    Store.putScrollRule((rule ?: ScrollRule(pkg)).copy(mode = if (on) ScrollMode.NORMAL else ScrollMode.OFF))
                }
                Spacer(Modifier.width(10.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.muted)
            }
        }
        item { Gap(24) }
    }

    if (picking) AppPicker(apps, Store.cooldownExtraApps, onDismiss = { picking = false }) { Store.cooldownExtraApps = it }
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

@Composable
fun AppDetailScreen(nav: Nav, pkg: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val rule = Store.scrollRule(pkg)
    fun put(r: ScrollRule) = Store.putScrollRule(r)
    var modeOpen by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Edit app", onBack)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(pkg, 44); Spacer(Modifier.width(14.dp)); Title(appLabel(ctx, pkg), 22)
        }
        if (Catalog.ReelsApp.byPkg(pkg) != null) {
            Gap(6)
            Card(Modifier.padding(horizontal = 12.dp), onClick = { nav.go("reels/sub") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Title("Block Reels and Shorts", modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.muted)
                }
            }
        }
        Gap(10)
        Card(Modifier.padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { modeOpen = true }, verticalAlignment = Alignment.CenterVertically) {
                Title("AntiScroll mode", modifier = Modifier.weight(1f))
                Text(rule.mode.label, color = C.pink, fontSize = 16.sp)
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = C.pink)
            }
            Gap()
            if (rule.mode == ScrollMode.CUSTOM) {
                val m = rule.customTriggerSec / 60
                val s = rule.customTriggerSec % 60
                Body("Alert when: ${rule.customMinScrolls} scroll${if (rule.customMinScrolls == 1) "" else "s"} every ${rule.customWindowSec}s for ${m}m ${s}s", color = C.text)
                Gap()
                LabeledStepper("Minimum scrolls", "Scrolls needed in each window", "${rule.customMinScrolls}",
                    { put(rule.copy(customMinScrolls = (rule.customMinScrolls - 1).coerceAtLeast(1))) },
                    { put(rule.copy(customMinScrolls = (rule.customMinScrolls + 1).coerceAtMost(30))) })
                LabeledStepper("Scrolling window", "Seconds to count scrolls in", "${rule.customWindowSec}s",
                    { put(rule.copy(customWindowSec = (rule.customWindowSec - 5).coerceAtLeast(5))) },
                    { put(rule.copy(customWindowSec = (rule.customWindowSec + 5).coerceAtMost(300))) })
                LabeledStepper("Trigger after", "Minutes of continuous scrolling", "${m}m",
                    { put(rule.copy(customTriggerSec = (rule.customTriggerSec - 60).coerceAtLeast(s.coerceAtLeast(10)))) },
                    { put(rule.copy(customTriggerSec = (rule.customTriggerSec + 60).coerceAtMost(4 * 3600))) })
                LabeledStepper("", "Plus seconds", "${s}s",
                    { put(rule.copy(customTriggerSec = (rule.customTriggerSec - 10).coerceAtLeast(10))) },
                    { put(rule.copy(customTriggerSec = (rule.customTriggerSec + 10).coerceAtMost(4 * 3600))) })
            } else {
                Body(rule.mode.blurb, color = C.text)
            }
        }
        Gap(10)
        Card(Modifier.padding(horizontal = 12.dp)) {
            Title("Popup timeout")
            Body("How long the popup makes you think before you can keep scrolling")
            Gap()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 120).forEach { sec -> Chip("$sec sec", rule.popupTimeoutSec == sec, Modifier.weight(1f)) { put(rule.copy(popupTimeoutSec = sec)) } }
            }
            Gap()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Body("Custom", color = C.text, modifier = Modifier.weight(1f))
                Stepper("${rule.popupTimeoutSec} sec",
                    { put(rule.copy(popupTimeoutSec = (rule.popupTimeoutSec - 5).coerceAtLeast(5))) },
                    { put(rule.copy(popupTimeoutSec = (rule.popupTimeoutSec + 5).coerceAtMost(600))) })
            }
        }
        Gap(24)
    }

    if (modeOpen) SquareDialog({ modeOpen = false }) {
        Title("AntiScroll mode", 18)
        Gap()
        ScrollMode.entries.forEach { m ->
            Row(Modifier.fillMaxWidth().clickable { put(rule.copy(mode = m)); modeOpen = false }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SquareCheck(rule.mode == m) { put(rule.copy(mode = m)); modeOpen = false }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(m.label, color = C.text, fontSize = 16.sp)
                    val hint = when (m) {
                        ScrollMode.OFF -> "No popups"
                        ScrollMode.CUSTOM -> "Set your own thresholds"
                        else -> "After about ${m.triggerSec / 60} min of scrolling"
                    }
                    Body(hint, size = 12)
                }
            }
        }
    }
}

@Composable
private fun LabeledStepper(title: String, sub: String, value: String, onDec: () -> Unit, onInc: () -> Unit) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (title.isNotEmpty()) Title(title, 15)
            Body(sub, size = 12)
        }
        Stepper(value, onDec, onInc)
    }
}
