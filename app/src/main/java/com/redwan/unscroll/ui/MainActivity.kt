package com.redwan.unscroll.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redwan.unscroll.data.Store
import com.redwan.unscroll.ui.screens.*

/** Simple back stack navigation. Routes are strings like "app/com.example". */
class Nav {
    val stack = mutableStateListOf("home")
    val current get() = stack.last()
    fun go(route: String) { stack.add(route) }
    fun tab(route: String) { stack.clear(); stack.add(route) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
}

class MainActivity : ComponentActivity() {
    private val nav = Nav()
    private var unlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Store.onboarded) nav.tab("setup")
        handle(intent)
        setContent {
            UnscrollTheme {
                Box(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding()) {
                    if (Store.hasPin && !unlocked) LockScreen { unlocked = true }
                    else App(nav)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra("route")?.let { if (Store.onboarded) nav.tab(it) }
    }

    override fun onStop() {
        super.onStop()
        // relock whenever the app leaves the screen
        if (!isChangingConfigurations) unlocked = false
    }
}

private val tabs = listOf(
    Triple("home", "Home", Icons.Filled.Home),
    Triple("reels", "AntiReels", Icons.Filled.SlowMotionVideo),
    Triple("scroll", "AntiScroll", Icons.Filled.Swipe),
    Triple("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun App(nav: Nav) {
    @Suppress("UNUSED_VARIABLE") val rev = Store.rev.intValue // recompose on any settings change
    BackHandler(enabled = nav.stack.size > 1 || nav.current != "home") {
        if (!nav.back()) nav.tab("home")
    }
    val route = nav.current
    val root = tabs.any { it.first == route }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            val back: () -> Unit = { if (!nav.back()) nav.tab("home") }
            when {
                route == "home" -> HomeScreen(nav)
                route == "reels" -> ReelsScreen(nav, onBack = null)
                route == "reels/sub" -> ReelsScreen(nav, onBack = back)
                route == "scroll" -> ScrollListScreen(nav)
                route == "settings" -> SettingsScreen(nav)
                route.startsWith("app/") -> AppDetailScreen(nav, route.removePrefix("app/"), back)
                route == "tips" -> TipsScreen(nav, back)
                route == "tips/why" -> WhyEditor(back)
                route == "tips/commit" -> CommitEditor(back)
                route == "tips/triggers" -> TriggersEditor(back)
                route == "tips/env" -> EnvironmentEditor(nav, back)
                route == "tips/brain" -> BrainScreen(back)
                route == "breaks" -> BreaksScreen(nav, back)
                route.startsWith("break/") -> BreakEditor(route.removePrefix("break/").toLong(), back)
                route == "insights" -> InsightsScreen(back)
                route == "messages" -> MessagesScreen(back)
                route == "sites" -> SitesScreen(back)
                route == "password" -> PasswordScreen(back)
                route == "trouble" -> TroubleshootingScreen(back)
                route == "setup" -> SetupScreen(onDone = { Store.onboarded = true; nav.tab("home") }, onBack = if (Store.onboarded) back else null)
                else -> HomeScreen(nav)
            }
        }
        if (root) BottomBar(route) { nav.tab(it) }
    }
}

@Composable
private fun BottomBar(current: String, onSelect: (String) -> Unit) {
    Column {
        Divider()
        Row(Modifier.fillMaxWidth().height(64.dp).background(C.bg)) {
            tabs.forEach { (route, label, icon) -> TabItem(label, icon, route == current, Modifier.weight(1f)) { onSelect(route) } }
        }
    }
}

@Composable
private fun TabItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxSize().clickable(onClick = onClick).padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (selected) C.pink else C.muted, modifier = Modifier.size(24.dp))
        Text(label, color = if (selected) C.pink else C.muted, fontSize = 12.sp)
    }
}
