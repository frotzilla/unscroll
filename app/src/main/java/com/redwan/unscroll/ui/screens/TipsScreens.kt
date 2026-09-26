package com.redwan.unscroll.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redwan.unscroll.data.Catalog
import com.redwan.unscroll.data.Store
import com.redwan.unscroll.data.TriggerPlan
import com.redwan.unscroll.ui.*
import com.redwan.unscroll.widget.WhyWidget

@Composable
fun TipsScreen(nav: Nav, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val plans = Store.triggerPlans
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Tips", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Body("Unscroll removes the triggers, but it is a tool to work with, not a replacement for your own intention. If you really want to, you will always find a way around it.", color = C.text)
            Gap(8)
            Body("Without a clear reason, the algorithm wins. With one, every nudge stops feeling like a wall and becomes a reminder of why you chose to stop.", color = C.text)
        }
        Gap()
        NavRow("How your brain works", icon = Icons.Outlined.Psychology) { nav.go("tips/brain") }
        Gap(6)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row {
                Body("Your progress", color = C.text, modifier = Modifier.weight(1f))
                Body("${Store.tipsProgress()} of 4", color = C.text)
            }
            Gap(6)
            Meter(Store.tipsProgress() / 4f)
        }
        Gap()
        TipCard("My why", Icons.Outlined.HelpOutline,
            Store.why.ifBlank { "Add your reasons and what scrolling is costing you. This is your anchor for the hard moments." }) { nav.go("tips/why") }
        TipCard("My commitment", Icons.Outlined.Flag,
            Store.commitmentText() ?: "Decide your exact rule, so there is nothing to negotiate later.") { nav.go("tips/commit") }
        TipCard("My replacements", Icons.Outlined.SwapHoriz,
            if (plans.isEmpty()) "No triggers named yet. Name the moments you scroll without deciding to, then plan what you do instead."
            else plans.joinToString("\n") { "When ${it.trigger.lowercase()}: ${it.instead.ifBlank { "(no plan yet)" }}" }) { nav.go("tips/triggers") }
        TipCard("My environment", Icons.Outlined.Tune,
            if (Store.environment.isEmpty()) "No environment rules set. These are the changes that work without willpower."
            else Store.environment.joinToString("\n") { "• $it" }) { nav.go("tips/env") }
        Gap(4)
        Card(Modifier.padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Widgets, null, tint = C.pink)
                Spacer(Modifier.width(10.dp))
                Title("Keep your why in sight", 16)
            }
            Gap(6)
            Body("Put the My why widget on your home screen so you see it before you open a feed.")
            Gap()
            PinkButton("Add widget") {
                val mgr = AppWidgetManager.getInstance(ctx)
                if (mgr.isRequestPinAppWidgetSupported) mgr.requestPinAppWidget(ComponentName(ctx, WhyWidget::class.java), null, null)
            }
        }
        Gap(24)
    }
}

@Composable
private fun TipCard(title: String, icon: ImageVector, body: String, onClick: () -> Unit) {
    Card(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = C.text)
            Spacer(Modifier.width(10.dp))
            Title(title, 16, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.Edit, "Edit", tint = C.muted)
        }
        Gap(6)
        Body(body)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhyEditor(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var why by remember { mutableStateOf(Store.why) }
    var costs by remember { mutableStateOf(Store.whyCosts) }
    var future by remember { mutableStateOf(Store.whyFuture) }
    var own by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Your why", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Body("Before any blocking helps, get clear on why this matters to you. When the pull is strong, this is what you come back to.", color = C.text)
            Gap(20)
            Title("Why do you want to change how you scroll?", 16)
            Body("Be specific. \"I want my evenings back to read and see my friends\" lands harder than \"I want to use my phone less.\"", size = 13)
            Gap(8)
            Field(why, { why = it }, "Be honest. This is just for you.", minLines = 3)
            Gap(20)
            Title("What is it costing you?", 16)
            Gap(8)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (Catalog.costs + costs.filter { it !in Catalog.costs }).forEach { c ->
                    Chip(c, c in costs) { costs = if (c in costs) costs - c else costs + c }
                }
            }
            Gap(8)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(own, { own = it }, "Add your own, like my side project", Modifier.weight(1f), singleLine = true)
                Icon(Icons.Filled.Add, "Add cost", tint = C.pink, modifier = Modifier.padding(start = 10.dp).clickable {
                    if (own.isNotBlank()) { costs = costs + own.trim(); own = "" }
                })
            }
            Gap(20)
            Title("Picture six months from now, if nothing changes, and if it does.", 16)
            Body("Optional", size = 13)
            Gap(8)
            Field(future, { future = it }, "What is different in the life you want?", minLines = 3)
            Gap(20)
            PinkButton("Save") {
                Store.why = why.trim(); Store.whyCosts = costs; Store.whyFuture = future.trim()
                WhyWidget.refresh(ctx)
                onBack()
            }
        }
        Gap(24)
    }
}

@Composable
fun CommitEditor(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var idx by remember { mutableStateOf(Store.commitmentIdx) }
    var custom by remember { mutableStateOf(Store.commitmentCustom) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Your commitment", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Body("Decide the exact rule you are committing to. If the rule is fuzzy, your mind finds an excuse in the moment. If it is specific, there is nothing to argue about. You just remember what you decided.", color = C.text)
            Gap(20)
            Title("What are you committing to?", 16)
            Gap(8)
            Catalog.commitments.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth().clickable { idx = i }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SquareCheck(idx == i) { idx = i }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(c.title, color = C.text, fontSize = 16.sp)
                        Body(c.detail, size = 13)
                    }
                }
            }
            if (idx == Catalog.commitments.lastIndex) {
                Gap(8)
                Field(custom, { custom = it }, "Your rule, in your own words", minLines = 2)
            }
            Gap()
            Card {
                Title("Why being specific matters", 15)
                Gap(4)
                Body("\"Use Instagram less\" gets renegotiated every evening. \"No Reels, and Instagram only after 7pm\" either happened or it did not. Clear rules turn a hundred small decisions into one.")
            }
            Gap(20)
            PinkButton("Save", enabled = idx >= 0) {
                Store.commitmentIdx = idx; Store.commitmentCustom = custom.trim()
                WhyWidget.refresh(ctx)
                onBack()
            }
        }
        Gap(24)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TriggersEditor(onBack: () -> Unit) {
    var plans by remember { mutableStateOf(Store.triggerPlans) }
    var showAll by remember { mutableStateOf(false) }
    var own by remember { mutableStateOf("") }
    val chosen = plans.map { it.trigger }.toSet()
    fun toggle(t: String) {
        plans = if (t in chosen) plans.filter { it.trigger != t } else plans + TriggerPlan(t, "")
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Your triggers", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Body("You rarely decide to scroll. A moment triggers it. Naming those moments is what lets you plan for them. Pick the ones that feel true.", color = C.text)
            Gap(20)
            Title("When do you reach for your phone without deciding to?", 16)
            Gap(8)
            val list = (Catalog.triggers + chosen.filter { it !in Catalog.triggers })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (showAll) list else list.take(6) + chosen.filter { it !in list.take(6) }).distinct().forEach { t -> Chip(t, t in chosen) { toggle(t) } }
            }
            if (!showAll) Text("Show ${list.size - 6} more", color = C.pink, modifier = Modifier.clickable { showAll = true }.padding(vertical = 10.dp))
            Gap(8)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(own, { own = it }, "Add your own, like right after checking the time", Modifier.weight(1f), singleLine = true)
                Icon(Icons.Filled.Add, "Add trigger", tint = C.pink, modifier = Modifier.padding(start = 10.dp).clickable {
                    if (own.isNotBlank()) { plans = plans + TriggerPlan(own.trim(), ""); own = "" }
                })
            }
            if (plans.isNotEmpty()) {
                Gap(24)
                Title("Your plan", 18)
                Body("Willpower runs out. Keep the trigger, swap what you do next. Decide it now, once, so in the moment you only run the plan.")
                plans.forEachIndexed { i, p ->
                    Gap()
                    Card {
                        Title("When ${p.trigger.lowercase()}", 15)
                        Gap(8)
                        Field(p.instead, { v -> plans = plans.toMutableList().also { it[i] = p.copy(instead = v) } }, "Instead, I will...", singleLine = true)
                        Gap(8)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Catalog.replacements.forEach { r ->
                                Chip(r, p.instead == r) { plans = plans.toMutableList().also { it[i] = p.copy(instead = r) } }
                            }
                        }
                    }
                }
            }
            Gap(20)
            PinkButton("Done") { Store.triggerPlans = plans; onBack() }
        }
        Gap(24)
    }
}

@Composable
fun EnvironmentEditor(nav: Nav, onBack: () -> Unit) {
    var env by remember { mutableStateOf(Store.environment) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Your environment", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Title("Set up your environment", 18)
            Body("Proven tactics that remove the temptation before it starts. Check the ones you will commit to.")
            Gap()
            Catalog.environment.forEach { e ->
                Row(Modifier.fillMaxWidth().clickable { env = if (e in env) env - e else env + e }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SquareCheck(e in env) { env = if (it) env + e else env - e }
                    Spacer(Modifier.width(14.dp))
                    Text(e, color = C.text, fontSize = 16.sp)
                }
            }
            Gap()
            Card {
                Title("Make it automatic with Unscroll", 16)
                Body("Let the app hold the line so you do not have to rely on willpower in the moment.")
                Gap()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("AntiReels", Modifier.weight(1f)) { Store.environment = env; nav.go("reels/sub") }
                    GhostButton("Breaks", Modifier.weight(1f)) { Store.environment = env; nav.go("breaks") }
                }
            }
            Gap(20)
            PinkButton("Done") { Store.environment = env; onBack() }
        }
        Gap(24)
    }
}

@Composable
fun BrainScreen(onBack: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("How your brain works", onBack)
        Catalog.brainLessons.forEachIndexed { i, l ->
            Card(Modifier.padding(horizontal = 12.dp, vertical = 5.dp)) {
                Text("${i + 1}", color = C.pink, fontSize = 13.sp)
                Title(l.title, 17)
                Gap(6)
                Body(l.body, color = C.text)
            }
        }
        Gap(24)
    }
}
