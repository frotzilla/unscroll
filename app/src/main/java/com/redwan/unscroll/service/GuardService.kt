package com.redwan.unscroll.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.redwan.unscroll.data.BreakRule
import com.redwan.unscroll.data.Catalog
import com.redwan.unscroll.data.ScrollRule
import com.redwan.unscroll.data.Store
import java.time.LocalDate
import java.time.ZoneId

class GuardService : AccessibilityService() {

    val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var overlays: Overlays
    private lateinit var reels: ReelsGuard
    private lateinit var sites: SiteGuard
    private lateinit var self: SelfGuard
    private val meter = ScrollMeter()

    private var fg = ""
    private var prevFg = ""
    private var fgSince = 0L
    private var countingSince = 0L // start of uncounted foreground time for the allowance
    private var screenOn = true
    private var ignored = setOf<String>()
    private var lastBack = 0L
    private var lastToast = 0L
    private var cooldownExempt: String? = null
    private var popupPkg: String? = null
    private var cmPerSwipe = 0

    // open with intent
    private val lastLeft = HashMap<String, Long>()
    private val browseUntil = HashMap<String, Long>()
    private val lastSwipe = HashMap<String, Long>()

    // settings cache, refreshed whenever the store changes
    private var cacheRev = -1
    private var rules = emptyMap<String, ScrollRule>()
    private var breaks = emptyList<BreakRule>()
    private var watched = emptySet<String>()
    private var lockoutApps = emptySet<String>()

    // throttle tree inspection per package
    private val pending = HashMap<String, Runnable>()
    private val lastRun = HashMap<String, Long>()
    private var lastDump = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            flushUsage()
            screenOn = i.action == Intent.ACTION_SCREEN_ON
            countingSince = System.currentTimeMillis()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            flushUsage()
            if (fg.isNotEmpty() && screenOn) enforce(fg, System.currentTimeMillis())
            mainHandler.postDelayed(this, 5000)
        }
    }

    override fun onServiceConnected() {
        instance = this
        overlays = Overlays(this)
        reels = ReelsGuard(this)
        sites = SiteGuard(this)
        self = SelfGuard(this)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        ignored = imm.enabledInputMethodList.map { it.packageName }.toSet() + setOf(packageName, "com.android.systemui")
        val dm = resources.displayMetrics
        // a swipe moves roughly 60 percent of the screen
        cmPerSwipe = (0.6 * dm.heightPixels / dm.ydpi * 2.54).toInt().coerceAtLeast(1)
        Store.serviceExpected = true
        rootInActiveWindow?.packageName?.toString()?.takeIf { it !in ignored }?.let {
            fg = it
            fgSince = System.currentTimeMillis()
        }
        countingSince = System.currentTimeMillis()
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
        mainHandler.postDelayed(ticker, 5000)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (instance == null) return
        instance = null
        flushUsage()
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        if (::overlays.isInitialized) overlays.clearAll()
    }

    override fun onInterrupt() {}

    private fun refreshCache() {
        val rev = Store.rev.intValue
        if (rev == cacheRev) return
        cacheRev = rev
        rules = Store.scrollRules()
        breaks = Store.breaks
        val reelsPkgs = if (Store.reelsMaster) Catalog.ReelsApp.entries.filter { Store.reelsApp(it) }.flatMap { listOf(it.pkg) + it.altPkgs() } else emptyList()
        lockoutApps = rules.values.filter { it.active }.map { it.pkg }.toSet() + Store.cooldownExtraApps
        watched = lockoutApps + rules.values.filter { it.any }.map { it.pkg } + reelsPkgs
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (pkg in ignored) return
        refreshCache()
        val now = System.currentTimeMillis()

        // resuming an app does not always send a window change, so also trust the active window
        if (pkg != fg && (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || rootInActiveWindow?.packageName == pkg)) {
            onForeground(pkg, now)
        }

        // uninstall protection keeps working while snoozed
        if (self.watches(pkg)) {
            schedule(pkg) { rootInActiveWindow?.takeIf { it.packageName == pkg }?.let(self::check) }
            return
        }
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) countDistance(pkg, now)
        if (Store.paused) {
            overlays.clearCovers()
            if (pkg == fg) enforce(pkg, now) // a focus session outlasts a pause
            return
        }
        if (pkg == fg && enforce(pkg, now)) return

        if (e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) onScroll(pkg, e, now)
        schedule(pkg) { inspect(pkg) }
    }

    private fun onForeground(pkg: String, now: Long) {
        flushUsage()
        if (fg.isNotEmpty()) lastLeft[fg] = now
        prevFg = fg
        fg = pkg
        fgSince = now
        overlays.clearCovers()
        reels.onLeft(prevFg)
        // leaving the app while a check-in or gate is up counts as closing it
        if ((overlays.modalIs("popup") || overlays.modalIs("intent")) && popupPkg != null && pkg != popupPkg) overlays.dismissModal()
        if (overlays.modalShowing && overlays.modalKind() in TIMED && pkg !in watched) overlays.dismissModal()
        if (!Store.paused) maybeAskIntent(pkg, now)
    }

    /** Was [pkg] opened from another app a moment ago (a shared link, not in app navigation)? */
    fun justSwitchedFromOtherApp(pkg: String) =
        fg == pkg && prevFg.isNotEmpty() && prevFg != pkg && System.currentTimeMillis() - fgSince < 3000

    private fun schedule(pkg: String, block: () -> Unit) {
        val now = System.currentTimeMillis()
        pending.remove(pkg)?.let(mainHandler::removeCallbacks)
        val wait = (THROTTLE_MS - (now - (lastRun[pkg] ?: 0))).coerceAtLeast(0)
        val r = Runnable {
            pending.remove(pkg)
            lastRun[pkg] = System.currentTimeMillis()
            runCatching(block)
        }
        pending[pkg] = r
        mainHandler.postDelayed(r, wait)
    }

    private fun onBreak(pkg: String, reelsSide: Boolean): Boolean {
        val b = Store.activeBreak(pkg, list = breaks) ?: return false
        return if (reelsSide) b.relaxReels else b.relaxScroll
    }

    // whole app blocks: focus sessions, used up allowances, lockouts

    /** Shows a full screen block if [pkg] should not be usable right now. True if blocked. */
    private fun enforce(pkg: String, now: Long): Boolean {
        if (pkg !in watched) return false
        if (overlays.modalIs("popup") || overlays.modalIs("intent")) return true

        val focusEnd = Store.focusUntil
        if (now < focusEnd) {
            block("focus", "FOCUS SESSION", "${label(pkg)} is off limits until your focus session ends.", focusEnd)
            return true
        }
        if (Store.paused) return false
        if (onBreak(pkg, false)) return false

        val rule = rules[pkg]
        if (rule != null && rule.dailyMin > 0 && Store.usedToday(pkg) >= rule.dailyMin * 60_000L) {
            block("allowance", "DAILY ALLOWANCE USED", "You spent your ${rule.dailyMin} minutes of ${label(pkg)} for today. It opens again at midnight.", midnight())
            return true
        }

        val lockEnd = Store.cooldownUntil
        if (Store.cooldownOn && now < lockEnd && pkg in lockoutApps && pkg != cooldownExempt) {
            block("cooldown", "LOCKOUT", "${label(pkg)} and your other limited apps open again when this reaches zero.", lockEnd)
            return true
        }
        if (now >= lockEnd) cooldownExempt = null

        browseUntil[pkg]?.let { end ->
            if (now >= end) {
                browseUntil.remove(pkg)
                showCheckIn(pkg, rule ?: ScrollRule(pkg), "Your 5 minutes in ${label(pkg)} are up.", now)
                return true
            }
        }
        return false
    }

    private fun block(kind: String, tag: String, body: String, until: Long) {
        overlays.clearCovers()
        overlays.showTimedBlock(kind, tag, body, until) { home() }
    }

    private fun midnight() = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun flushUsage() {
        val now = System.currentTimeMillis()
        if (screenOn && fg.isNotEmpty() && rules[fg]?.dailyMin?.let { it > 0 } == true) Store.addUsed(fg, now - countingSince)
        countingSince = now
    }

    // open with intent

    private fun maybeAskIntent(pkg: String, now: Long) {
        val rule = rules[pkg] ?: return
        if (!rule.askIntent || onBreak(pkg, false)) return
        // coming back within a minute (say, from a share sheet) is the same visit
        if (now - (lastLeft[pkg] ?: 0) < 60_000) return
        if (enforce(pkg, now)) return
        popupPkg = pkg
        overlays.clearCovers()
        overlays.showIntentGate(label(pkg), onChoice = { browsing ->
            popupPkg = null
            if (browsing) browseUntil[pkg] = System.currentTimeMillis() + BROWSE_MS else browseUntil.remove(pkg)
        }, onLeave = {
            popupPkg = null
            Store.bump(Store.Counter.RECONSIDERED)
            home()
        })
    }

    // scrolling

    private fun countDistance(pkg: String, now: Long) {
        if (now - (lastSwipe[pkg] ?: 0) < ScrollMeter.GESTURE_MS) return
        lastSwipe[pkg] = now
        Store.add(Store.Counter.SCROLL_CM, cmPerSwipe)
    }

    private fun onScroll(pkg: String, e: AccessibilityEvent, now: Long) {
        reels.onScroll(pkg, e)
        val rule = rules[pkg] ?: return
        if (!rule.active || onBreak(pkg, false) || overlays.modalShowing) return
        // ignore small carousels; a feed scroll comes from a view taking most of the screen
        e.source?.let { if (Nodes.bounds(it).height() < screenHeight() * 0.35f) return }
        if (meter.onScroll(rule, now)) {
            val limit = if (rule.limitSec >= 60) "${rule.limitSec / 60} min" else "${rule.limitSec}s"
            showCheckIn(pkg, rule, "$limit of nonstop scrolling in ${label(pkg)}.", now)
        }
    }

    /** Foreground time in [pkg] today, from usage stats (null without usage access). */
    private fun todayIn(pkg: String): Long? = runCatching {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())[pkg]?.totalTimeInForeground
    }.getOrNull()?.takeIf { it > 0 }

    private fun showCheckIn(pkg: String, rule: ScrollRule, headline: String, now: Long) {
        Store.bump(Store.Counter.POPUPS)
        if (Store.cooldownOn && rule.active) {
            Store.cooldownUntil = now + Store.cooldownMin * 60_000L
            cooldownExempt = null
            Store.bump(Store.Counter.COOLDOWNS)
        }
        popupPkg = pkg
        overlays.clearCovers()
        overlays.showCheckIn(
            headline = headline,
            detail = todayIn(pkg)?.let { "${com.redwan.unscroll.data.Usage.fmt(it)} in ${label(pkg)} today." },
            waitSec = rule.waitSec,
            onClose = { popupPkg = null; meter.reset(pkg); home() },
            onContinue = { popupPkg = null; meter.reset(pkg); cooldownExempt = pkg },
        )
    }

    // screen inspection

    private fun inspect(pkg: String) {
        if (pkg != fg || Store.paused || overlays.modalShowing) {
            if (overlays.modalShowing) overlays.clearCovers()
            return
        }
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        if (Store.diagnostics && System.currentTimeMillis() - lastDump > 2500) {
            lastDump = System.currentTimeMillis()
            Nodes.dump("UnscrollDiag/$pkg", root)
        }

        if (sites.isBrowser(pkg)) {
            sites.check(pkg, root)
            return
        }

        if (pkg == MESSENGER && Store.reelsMaster && Store.reelsApp(Catalog.ReelsApp.FACEBOOK) && !onBreak(pkg, true)) {
            reels.checkMessenger(pkg, root)
            return
        }

        val app = Catalog.ReelsApp.byPkg(pkg)
        if (app == null || !Store.reelsMaster || !Store.reelsApp(app) || onBreak(pkg, true)) {
            overlays.clearCovers()
            return
        }
        val covers = reels.check(app, pkg, root)
        overlays.setCovers(covers.rects, covers.touchable)
    }

    // actions used by the guards

    fun back() {
        val now = System.currentTimeMillis()
        if (now - lastBack < 700) return
        lastBack = now
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun home() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun blocked(what: String) {
        Store.bump(Store.Counter.REELS)
        toast("Unscroll blocked $what")
    }

    fun toast(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastToast < 2500) return
        lastToast = now
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    fun screenHeight() = resources.displayMetrics.heightPixels

    private val labels = HashMap<String, String>()
    fun label(pkg: String) = labels.getOrPut(pkg) {
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    companion object {
        const val THROTTLE_MS = 250L
        const val BROWSE_MS = 5 * 60_000L
        const val MESSENGER = "com.facebook.orca"
        private val TIMED = setOf("focus", "allowance", "cooldown")

        @Volatile
        var instance: GuardService? = null
            private set

        val running get() = instance != null

        fun isEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(ctx, GuardService::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun isInstalled(pm: PackageManager, pkg: String) =
            runCatching { pm.getApplicationInfo(pkg, 0); true }.getOrDefault(false)
    }
}
