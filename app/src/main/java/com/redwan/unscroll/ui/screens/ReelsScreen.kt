package com.redwan.unscroll.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redwan.unscroll.data.Catalog.ReelsApp
import com.redwan.unscroll.data.Store
import com.redwan.unscroll.service.GuardService
import com.redwan.unscroll.ui.*

@Composable
fun ReelsScreen(nav: Nav, onBack: (() -> Unit)?) {
    val ctx = LocalContext.current
    var help by remember { mutableStateOf(false) }
    val pm = ctx.packageManager
    val (installed, missing) = ReelsApp.entries.partition { app -> (listOf(app.pkg) + app.altPkgs()).any { GuardService.isInstalled(pm, it) } }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar("AntiReels", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title("Block Reels and Shorts", 22, modifier = Modifier.weight(1f))
                SquareSwitch(Store.reelsMaster, { Store.reelsMaster = it })
            }
            Row(Modifier.clickable { help = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = C.muted)
                Spacer(Modifier.width(6.dp))
                Text("Not working? Tap here", color = C.muted, fontSize = 14.sp, textDecoration = TextDecoration.Underline)
            }
        }
        Gap(4)
        Divider()

        installed.forEach { app ->
            Gap(10)
            AppCard(app, enabled = Store.reelsMaster)
        }
        if (missing.isNotEmpty()) {
            Section("Not installed")
            Card(Modifier.padding(horizontal = 12.dp)) {
                missing.forEach { app ->
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Title(app.label, 15, modifier = Modifier.weight(1f))
                        Body("Not installed", size = 13)
                    }
                }
                Body("Blocking turns on by itself when you install one of these.", size = 12)
            }
        }
        Gap(24)
    }

    if (help) SquareDialog({ help = false }) {
        Title("If blocking is not working", 19)
        Gap()
        listOf(
            "Make sure the Unscroll accessibility service is on. Android turns it off after some updates.",
            "Samsung and other brands kill background apps. In Troubleshooting, remove battery restrictions for Unscroll.",
            "Update the app you are trying to block. Very old and very new beta versions can use different screens.",
            "If a black box gets stuck on screen, turn off the option that hides parts of the feed for that app.",
            "Still stuck? Turn on diagnostics in Troubleshooting and look at the log.",
        ).forEach { Body("• $it", color = C.text, modifier = Modifier.padding(bottom = 8.dp)) }
        Gap()
        PinkButton("Open Troubleshooting") { help = false; nav.go("trouble") }
    }
}

@Composable
private fun AppCard(app: ReelsApp, enabled: Boolean) {
    val on = Store.reelsApp(app)
    Card(Modifier.padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.pkg, 30)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Title(app.label)
                Body("Blocks ${app.surface}", size = 12)
            }
            SquareSwitch(on, { Store.setReelsApp(app, it) }, enabled)
        }
        if (!on || !enabled) return@Card
        when (app) {
            ReelsApp.INSTAGRAM -> InstagramOptions()
            ReelsApp.FACEBOOK -> FacebookOptions()
            ReelsApp.YOUTUBE -> YouTubeOptions()
            ReelsApp.TIKTOK -> TikTokOptions()
            else -> {}
        }
    }
}

@Composable
private fun OptionGroup(title: String, content: @Composable () -> Unit) {
    Gap(14)
    Text(title.uppercase(), color = C.muted, fontSize = 12.sp, letterSpacing = 1.sp)
    Gap(4)
    Column(Modifier.fillMaxWidth().background(C.card2).padding(horizontal = 12.dp, vertical = 4.dp)) { content() }
}

@Composable
private fun Opt(key: String, title: String, sub: String? = null, def: Boolean = false, badge: String? = null, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) =
    ToggleRow(title, sub, Store.opt(key, def), badge = badge, icon = icon) { Store.setOpt(key, it) }

@Composable
private fun Radio(label: String, sub: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(start = 36.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareCheck(selected) { if (enabled) onClick() }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = if (enabled) C.text else C.muted, fontSize = 15.sp)
            if (sub != null) Body(sub, size = 12)
        }
    }
}

/** A row that cycles through [options] when tapped. */
@Composable
fun ChoiceRow(title: String, key: String, options: List<Pair<String, String>>, def: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, enabled: Boolean = true) {
    val cur = Store.choice(key, def)
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { open = true }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = C.text); Spacer(Modifier.width(14.dp)) }
        Title(title, 16, color = if (enabled) C.text else C.muted, modifier = Modifier.weight(1f))
        Text(options.firstOrNull { it.first == cur }?.second ?: cur, color = C.pink, fontSize = 15.sp)
    }
    if (open) SquareDialog({ open = false }) {
        Title(title, 18)
        Gap()
        options.forEach { (v, l) ->
            Row(Modifier.fillMaxWidth().clickable { Store.setChoice(key, v); open = false }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SquareCheck(v == cur) { Store.setChoice(key, v); open = false }
                Spacer(Modifier.width(12.dp))
                Text(l, color = C.text, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun InstagramOptions() {
    OptionGroup("Main feed") {
        Opt("ig_hide_home_reels", "Hide Reels on Home", "Covers reels mixed into your feed. Turn off if a black box gets stuck.", def = true, icon = Icons.Outlined.VisibilityOff)
        Opt("ig_block_feed", "Block main feed", icon = Icons.Outlined.Home)
        val feedOn = Store.opt("ig_block_feed")
        val mode = Store.choice("ig_feed_mode", "hide")
        Radio("Hide feed", "Stories stay visible, the rest of the feed is covered", mode == "hide", feedOn) { Store.setChoice("ig_feed_mode", "hide") }
        Radio("Auto open DMs", "You go straight to your messages, the feed never shows", mode == "dms", feedOn) { Store.setChoice("ig_feed_mode", "dms") }
    }
    OptionGroup("Other filters") {
        Opt("ig_allow_dm", "Allow Reels sent in DMs", "Watch the reel a friend sent. Swiping to the next one is still blocked.", icon = Icons.Outlined.Send)
        Opt("ig_block_stories", "Block Stories", icon = Icons.Outlined.AutoStories)
        Opt("ig_block_comments", "Block post comments", icon = Icons.Outlined.ChatBubbleOutline)
        Opt("ig_hide_explore", "Hide feed on Explore", "Search still works", icon = Icons.Outlined.Explore)
    }
}

@Composable
private fun FacebookOptions() {
    OptionGroup("Reels") {
        ChoiceRow("Redirect Reels tab to", "fb_redirect", listOf("home" to "Home", "back" to "Previous screen", "close" to "Close Facebook"), "home", icon = Icons.Outlined.ViewDay)
        Opt("fb_allow_msg", "Allow Reels sent in messages", "Watch a reel a friend sent in Messenger. Scrolling to the next one is still blocked.", icon = Icons.Outlined.Send)
    }
    OptionGroup("Main feed") {
        Opt("fb_block_feed", "Block main feed", badge = "BETA", icon = Icons.Outlined.Home)
        ChoiceRow(
            "Instead of the feed, open", "fb_feed_target",
            listOf("profile" to "Profile", "notifications" to "Notifications", "friends" to "Friends", "menu" to "Menu"),
            "profile", enabled = Store.opt("fb_block_feed"),
        )
    }
}

@Composable
private fun YouTubeOptions() {
    OptionGroup("Filters") {
        Opt("yt_allow_link", "Allow Shorts opened from links", "A Short someone sends you opens once. Swiping to the next one is blocked.", icon = Icons.Outlined.Link)
        Opt("yt_hide_shelf", "Hide Shorts shelves", "Covers Shorts rows in Home, Search, and Subscriptions.", badge = "BETA", icon = Icons.Outlined.VisibilityOff)
    }
}

@Composable
private fun TikTokOptions() {
    OptionGroup("Filters") {
        Opt("tt_allow_dm", "Allow videos sent in DMs", "Opening a video from a chat works. The For You feed stays blocked.", icon = Icons.Outlined.Send)
    }
}
