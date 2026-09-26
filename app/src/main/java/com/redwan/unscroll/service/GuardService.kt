package com.redwan.unscroll.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
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
    private var ignored = setOf<String>()
    private var lastBack = 0L
    private var lastToast = 0L
    private var cooldownExempt: String? = null
    private var popupPkg: String? = null
    private var messageIdx = 0

    // settings cache, refreshed whenever the store changes
    private var cacheRev = -1
    private var rules = emptyMap<String, ScrollRule>()
    private var breaks = emptyList<BreakRule>()
    private var cooldownApps = emptySet<String>()

    // throttle tree inspection per package
    private val pending = HashMap<String, Runnable>()
    private val lastRun = HashMap<String, Long>()
    private var lastDump = 0L

    override fun onServiceConnected() {
        instance = this
        overlays = Overlays(this)
        reels = ReelsGuard(this)
        sites = SiteGuard(this)
        self = SelfGuard(this)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        ignored = imm.enabledInputMethodList.map { it.packageName }.toSet() + setOf(packageName, "com.android.systemui")
        Store.serviceExpected = true
        rootInActiveWindow?.packageName?.toString()?.takeIf { it !in ignored }?.let { fg = it; fgSince = System.currentTimeMillis() }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        if (::overlays.isInitialized) overlays.clearAll()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    private fun refreshCache() {
        val rev = Store.rev.intValue
        if (rev == cacheRev) return
        cacheRev = rev
        rules = Store.scrollRules()
        breaks = Store.breaks
        cooldownApps = rules.values.filter { it.active }.map { it.pkg }.toSet() + Store.cooldownExtraApps
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

        // uninstall protection keeps working while paused
        if (self.watches(pkg)) {
            schedule(pkg) { rootInActiveWindow?.takeIf { it.packageName == pkg }?.let(self::check) }
            return
        }
        if (Store.paused) {
            overlays.clearCovers()
            return
        }

        if (checkCooldown(pkg, now)) return

        if (e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) onScroll(pkg, e, now)
        schedule(pkg) { inspect(pkg) }
    }

    private fun onForeground(pkg: String, now: Long) {
        prevFg = fg
        fg = pkg
        fgSince = now
        overlays.clearCovers()
        reels.onLeft(prevFg)
        // leaving the app while the popup is up counts as closing it
        if (overlays.modalIs("popup") && popupPkg != null && pkg != popupPkg) overlays.dismissModal()
        if (overlays.modalIs("cooldown") && pkg !in cooldownApps) overlays.dismissModal()
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

    private fun onScroll(pkg: String, e: AccessibilityEvent, now: Long) {
        reels.onScroll(pkg, e)
        val rule = rules[pkg] ?: return
        if (!rule.active || onBreak(pkg, false) || overlays.modalShowing) return
        // ignore small carousels; a feed scroll comes from a view taking most of the screen
        e.source?.let { if (Nodes.bounds(it).height() < screenHeight() * 0.35f) return }
        if (meter.onScroll(rule, now)) showPopup(pkg, rule, now)
    }

    private fun showPopup(pkg: String, rule: ScrollRule, now: Long) {
        Store.bump(Store.Counter.POPUPS)
        val msgs = Store.messages.ifEmpty { Catalog.defaultMessages }
        val msg = msgs[messageIdx++ % msgs.size]
        if (Store.cooldownOn) {
            Store.cooldownUntil = now + Store.cooldownMin * 60_000L
            cooldownExempt = null
            Store.bump(Store.Counter.COOLDOWNS)
        }
        popupPkg = pkg
        overlays.clearCovers()
        overlays.showScrollPopup(
            appLabel = label(pkg),
            minutes = rule.triggerSec / 60,
            message = msg,
            timeoutSec = rule.popupTimeoutSec,
            onClose = { popupPkg = null; meter.reset(pkg); home() },
            onContinue = { popupPkg = null; meter.reset(pkg); cooldownExempt = pkg },
        )
    }

    /** Shows the cooldown screen when a locked app comes up. True if it is showing. */
    private fun checkCooldown(pkg: String, now: Long): Boolean {
        if (!Store.cooldownOn) return false
        val until = Store.cooldownUntil
        if (now >= until) {
            cooldownExempt = null
            return false
        }
        if (pkg !in cooldownApps || pkg == cooldownExempt || pkg != fg) return false
        if (overlays.modalIs("popup")) return true
        overlays.clearCovers()
        overlays.showCooldown(label(pkg), until) { home() }
        return true
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
        const val MESSENGER = "com.facebook.orca"

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
