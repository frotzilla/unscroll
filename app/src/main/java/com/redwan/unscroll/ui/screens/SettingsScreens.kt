package com.redwan.unscroll.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.ViewAgenda
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redwan.unscroll.admin.AdminReceiver
import com.redwan.unscroll.data.Catalog
import com.redwan.unscroll.data.Store
import com.redwan.unscroll.data.Usage
import com.redwan.unscroll.service.GuardService
import com.redwan.unscroll.ui.*
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    var confirmGuardOff by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Settings")

        Section("Protection")
        Column(Modifier.padding(horizontal = 16.dp)) {
            ToggleRow("Make snoozing harder", "After ${Store.snoozeFreeCount} snooze${if (Store.snoozeFreeCount == 1) "" else "s"} in a day, the next one needs a typed sentence", Store.antiPause, icon = Icons.Outlined.Spellcheck) { Store.antiPause = it }
            if (Store.antiPause) {
                Row(Modifier.padding(start = 36.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Body("Free snoozes a day", color = C.text, modifier = Modifier.weight(1f))
                    Stepper("${Store.snoozeFreeCount}", { Store.snoozeFreeCount = (Store.snoozeFreeCount - 1).coerceAtLeast(0) }, { Store.snoozeFreeCount = (Store.snoozeFreeCount + 1).coerceAtMost(10) })
                }
            }
            ToggleRow("Hide the snooze button", "Removes it from Today", Store.hidePause, icon = Icons.Outlined.PauseCircle) { Store.hidePause = it }
            ToggleRow("Uninstall guard", "Backs out of screens that would remove, stop, or switch off Unscroll", Store.uninstallProtect, icon = Icons.Outlined.PhonelinkLock) { on ->
                if (on) {
                    Store.uninstallProtect = true
                    if (!AdminReceiver.isActive(ctx)) runCatching { ctx.startActivity(AdminReceiver.requestIntent(ctx)) }
                } else {
                    confirmGuardOff = true
                }
            }
        }
        NavRow("PIN lock", "Ask for a PIN every time Unscroll opens", if (Store.hasPin) "On" else "Off", icon = Icons.Outlined.Lock) { nav.go("password") }

        Section("Web")
        Column(Modifier.padding(horizontal = 16.dp)) {
            ToggleRow("Filter adult sites", "Built in list, in supported browsers", Store.adultBlock, icon = Icons.Outlined.Block) { Store.adultBlock = it }
        }
        NavRow("Blocked sites", "${Store.blockedSites.size} of your own, plus where to land instead", icon = Icons.Outlined.Checklist) { nav.go("sites") }

        Section("About")
        NavRow("Setup and permissions", icon = Icons.Outlined.Checklist) { nav.go("setup") }
        NavRow("Troubleshooting", "If blocking stops working", icon = Icons.Outlined.Build) { nav.go("trouble") }
        NavRow("Tell a friend", icon = Icons.Outlined.Share) { share(ctx) }
        Gap(20)
        Text(
            "Unscroll ${ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName}. Runs entirely on this phone.", color = C.muted, fontSize = 12.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Gap(24)
    }

    if (confirmGuardOff) SquareDialog({ confirmGuardOff = false }) {
        Title("Switch off the uninstall guard?", 18)
        Gap(6)
        Body("Unscroll will be removable again and will stop watching its own settings screens.")
        Gap()
        PinkButton("Keep it on") { confirmGuardOff = false }
        Gap(8)
        GhostButton("Switch off") {
            Store.uninstallProtect = false
            runCatching { AdminReceiver.remove(ctx) }
            confirmGuardOff = false
        }
    }
}

@Composable
fun SitesScreen(onBack: () -> Unit) {
    var site by remember { mutableStateOf("") }
    var safe by remember { mutableStateOf(Store.safeUrl) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Blocked sites", onBack)
        Column(Modifier.padding(horizontal = 12.dp)) {
            Card { ToggleRow("Filter adult sites", "Built in list of domains and keywords", Store.adultBlock) { Store.adultBlock = it } }
            Section("Your list")
            Body("A domain such as reddit.com covers its subdomains too. A single word blocks any address containing it.")
            Gap(8)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(site, { site = it }, "example.com", Modifier.weight(1f), singleLine = true)
            }
            Gap(8)
            PinkButton("Add", enabled = site.isNotBlank()) {
                Store.blockedSites = Store.blockedSites + site.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
                site = ""
            }
            Gap()
            Store.blockedSites.sorted().forEach { s ->
                Row(Modifier.fillMaxWidth().clip(Round).background(C.card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Body(s, color = C.text, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, "Remove", tint = C.muted, modifier = Modifier.clickable { Store.blockedSites = Store.blockedSites - s })
                }
                Gap(6)
            }
            Section("Land here instead")
            Body("Opens in place of a blocked site. Leave it empty to simply go back.")
            Gap(8)
            Field(safe, { safe = it }, "https://...", singleLine = true)
            Gap(8)
            PinkButton("Save", enabled = safe != Store.safeUrl) { Store.safeUrl = safe.trim() }
            Section("Works in")
            Body("Chrome, Vivaldi, Brave, Edge, Firefox, Samsung Internet, Opera, DuckDuckGo, Kiwi, Ecosia. Blocking needs the Unscroll accessibility service.")
        }
        Gap(24)
    }
}

@Composable
fun PasswordScreen(onBack: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("PIN lock", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Body("With a PIN, changing any rule takes a deliberate step. It is asked for each time Unscroll opens. Forgetting it means clearing the app, so choose carefully.")
            Gap()
            if (Store.hasPin) {
                Field(current, { current = it.filter(Char::isDigit).take(8) }, "Current PIN", singleLine = true, number = true, secret = true)
                Gap(8)
            }
            Field(pin, { pin = it.filter(Char::isDigit).take(8) }, "New PIN (4 to 8 digits)", singleLine = true, number = true, secret = true)
            Gap(8)
            Field(confirm, { confirm = it.filter(Char::isDigit).take(8) }, "Repeat new PIN", singleLine = true, number = true, secret = true)
            Gap()
            PinkButton(if (Store.hasPin) "Change PIN" else "Turn on", enabled = pin.length >= 4 && pin == confirm) {
                if (Store.hasPin && !Store.checkPin(current)) { msg = "Current PIN is wrong"; return@PinkButton }
                Store.setPin(pin); msg = "PIN saved"; current = ""; pin = ""; confirm = ""
            }
            if (Store.hasPin) {
                Gap(8)
                GhostButton("Turn off password", enabled = current.isNotEmpty()) {
                    if (!Store.checkPin(current)) { msg = "Current PIN is wrong"; return@GhostButton }
                    Store.setPin(null); msg = "Password removed"; current = ""
                }
            }
            msg?.let { Gap(); Body(it, color = C.pink) }
        }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.Lock, null, tint = C.pink, modifier = Modifier.size(40.dp))
        Gap()
        Title("Enter PIN", 22)
        Gap(6)
        Body(if (wrong > 0) "That was not it" else "Unscroll settings are locked", color = if (wrong > 0) C.warn else C.muted)
        Gap(20)
        Text("● ".repeat(pin.length).ifEmpty { " " }, color = C.text, fontSize = 22.sp)
        Gap(20)
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "<")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(76.dp, 60.dp).then(if (k.isEmpty()) Modifier else Modifier.clip(Round).border(1.dp, C.line, Round).clickable {
                            if (k == "<") pin = pin.dropLast(1)
                            else if (pin.length < 8) {
                                pin += k
                                if (pin.length >= 4 && Store.checkPin(pin)) onUnlock()
                            }
                        }),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == "<") Icon(Icons.Filled.Backspace, "Delete", tint = C.text)
                        else Text(k, color = C.text, fontSize = 24.sp)
                    }
                }
            }
            Gap(12)
        }
        Gap()
        GhostButton("Submit", enabled = pin.length >= 4) { if (Store.checkPin(pin)) onUnlock() else { wrong++; pin = "" } }
    }
}

fun batteryIgnored(ctx: Context) = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

@SuppressLint("BatteryLife")
fun requestBattery(ctx: Context) {
    runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))) }
        .onFailure { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}

fun openAccessibility(ctx: Context) = ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

fun openAppInfo(ctx: Context) =
    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))

/** Re-checks permission state every second so the screen updates when the user comes back. */
@Composable
private fun rememberTicker(): Int {
    var t by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); t++ } }
    return t
}

@Composable
private fun StatusRow(label: String, ok: Boolean, okText: String = "On", badText: String = "Off") {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Body(label, color = C.text, modifier = Modifier.weight(1f))
        Text(if (ok) okText else badText, color = if (ok) C.pink else C.warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TroubleshootingScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val tick = rememberTicker()
    val samsung = Build.MANUFACTURER.equals("samsung", true)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Troubleshooting", onBack)
        Card(Modifier.padding(horizontal = 12.dp)) {
            Title("Status")
            Gap(6)
            StatusRow("Accessibility service enabled", GuardService.isEnabled(ctx))
            StatusRow("Service running", GuardService.running, "Yes", "No")
            StatusRow("Battery restrictions removed", batteryIgnored(ctx), "Yes", "No")
            StatusRow("Usage access", Usage.granted(ctx), "Yes", "No")
            StatusRow("Device admin (uninstall protection)", AdminReceiver.isActive(ctx))
        }
        Section("Fixes")
        NavRow("Restart accessibility service", "Turns it off so you can switch it back on in Settings", icon = Icons.Outlined.Build) {
            GuardService.instance?.disableSelf()
            openAccessibility(ctx)
        }
        NavRow("Remove battery restrictions", "Stops Android from killing Unscroll in the background", icon = Icons.Outlined.PauseCircle) { requestBattery(ctx) }
        if (samsung) {
            Card(Modifier.padding(horizontal = 12.dp)) {
                Title("On Samsung", 15)
                Gap(6)
                listOf(
                    "Settings, Battery, Background usage limits: add Unscroll to Never sleeping apps.",
                    "Settings, Apps, Unscroll, Battery: choose Unrestricted.",
                    "Recent apps screen: tap the Unscroll icon and choose Keep open.",
                ).forEach { Body("• $it", color = C.text, modifier = Modifier.padding(bottom = 6.dp)) }
            }
        }
        NavRow("Clear cache and data", "Opens app info. Clearing data resets every setting.", icon = Icons.Outlined.DeleteForever) { openAppInfo(ctx) }
        Section("For tuning")
        Column(Modifier.padding(horizontal = 16.dp)) {
            ToggleRow(
                "Diagnostics log", "Writes the view ids of the app on screen to logcat (tag UnscrollDiag). Never records typed text.",
                Store.diagnostics, icon = Icons.Outlined.BugReport,
            ) { Store.diagnostics = it }
        }
        Gap(24)
    }
}

@Composable
fun SetupScreen(onDone: () -> Unit, onBack: (() -> Unit)?) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val tick = rememberTicker()
    var disclosure by remember { mutableStateOf(false) }
    val a11y = GuardService.isEnabled(ctx)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("Set up Unscroll", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Title("Welcome to Unscroll", 22)
            Gap(6)
            Body("It blocks the endless parts of apps, like Reels and Shorts, and helps you set limits you will actually keep. Everything stays on this phone.")
        }
        Gap()
        Step(1, "Accessibility service", "Needed. Lets Unscroll recognise short videos and feeds.", a11y) { disclosure = true }
        Step(2, "Battery restrictions", "Recommended. Stops Android from closing Unscroll to save power.", batteryIgnored(ctx)) { requestBattery(ctx) }
        Step(3, "Usage access", "Optional. Shows your screen time on Today.", Usage.granted(ctx)) { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        Gap(20)
        Column(Modifier.padding(horizontal = 16.dp)) {
            PinkButton(if (a11y) "Start" else "Later", onClick = onDone)
        }
        Gap(24)
    }

    if (disclosure) SquareDialog({ disclosure = false }) {
        Title("About the accessibility permission", 18)
        Gap(8)
        listOf(
            "It reads which app is open and the structure of its screen, so it can recognise Reels, Shorts, feeds, and browser addresses.",
            "It can press Back or Home, and draw covers or a check in screen over other apps.",
            "It never records what you type, never takes screenshots, and sends nothing off your phone.",
        ).forEach { Body("• $it", color = C.text, modifier = Modifier.padding(bottom = 8.dp)) }
        Gap(4)
        Body("Next, find Unscroll protection under Installed apps (sometimes called Downloaded apps) and switch it on.")
        Gap()
        PinkButton("Open settings") { disclosure = false; openAccessibility(ctx) }
        Gap(8)
        GhostButton("Not now") { disclosure = false }
    }
}

@Composable
private fun Step(n: Int, title: String, sub: String, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clip(Round).background(C.card).clickable(enabled = !done, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (done) C.pink else C.card2), contentAlignment = Alignment.Center) {
            Text(if (done) "✓" else "$n", color = if (done) androidx.compose.ui.graphics.Color.Black else C.text, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Title(title, 16)
            Body(sub, size = 13)
        }
        Text(if (done) "Done" else "Allow", color = if (done) C.muted else C.pink)
    }
}
