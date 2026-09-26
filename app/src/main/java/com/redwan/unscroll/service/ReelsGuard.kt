package com.redwan.unscroll.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.redwan.unscroll.data.Catalog.ReelsApp
import com.redwan.unscroll.data.Store

/**
 * AntiReels: recognises short form video screens and steps out of them, and hides feed areas the
 * user asked to hide. View ids come from the current app versions and are the part most likely
 * to need updating when those apps change.
 */
class ReelsGuard(private val svc: GuardService) {

    /** Areas to cover in the current app, and whether the covers should swallow touches. */
    data class Covers(val rects: List<Rect>, val touchable: Boolean)

    private val lastDmSeen = HashMap<String, Long>()
    private val allowedSince = HashMap<String, Long>()

    private fun now() = System.currentTimeMillis()

    /** Inspects the current screen of [app]. Returns covers to draw (possibly empty). */
    fun check(app: ReelsApp, pkg: String, root: AccessibilityNodeInfo): Covers {
        return when (app) {
            ReelsApp.INSTAGRAM -> instagram(pkg, root)
            ReelsApp.YOUTUBE -> youtube(pkg, root)
            ReelsApp.FACEBOOK -> facebook(pkg, root)
            ReelsApp.TIKTOK -> tiktok(pkg, root)
            else -> generic(app, pkg, root)
        }
    }

    /** Messenger is not a Reels app itself but hosts reels shared from Facebook. */
    fun checkMessenger(pkg: String, root: AccessibilityNodeInfo) {
        val viewer = Nodes.byLabel(root, Regex("(?i)^(reels?|reel viewer)$")) ?: return
        if (Nodes.bounds(viewer).top > svc.screenHeight() / 4) return
        if (Store.opt("fb_allow_msg")) {
            allowedSince.putIfAbsent(pkg, now())
            return
        }
        svc.back()
        svc.blocked("Reels")
    }

    /** A scroll while inside an allowed single reel (from a DM or a link) means "next reel": block it. */
    fun onScroll(pkg: String, e: AccessibilityEvent) {
        val since = allowedSince[pkg] ?: return
        if (now() - since < 1500) return
        val src = e.source ?: return
        val b = Nodes.bounds(src)
        if (b.height() < svc.screenHeight() * 0.6f) return
        allowedSince.remove(pkg)
        svc.back()
        svc.blocked("the next reel")
    }

    fun onLeft(pkg: String) {
        allowedSince.remove(pkg)
    }

    /** Opened from a DM (or link) moments ago, and the feature allows it. */
    private fun allowSingle(pkg: String, enabled: Boolean, cameFromOutside: Boolean = false): Boolean {
        if (!enabled) return false
        if (allowedSince.containsKey(pkg)) return true
        val dm = lastDmSeen[pkg] ?: 0
        if (now() - dm < 5000 || cameFromOutside) {
            allowedSince[pkg] = now()
            return true
        }
        return false
    }

    // Instagram

    private val igClips = listOf(
        "clips_viewer_view_pager", "clips_viewer_container", "clips_video_container",
        "clips_swipe_refresh_container", "clips_viewer_fragment_container", "clips_single_media_viewer_container",
    )
    private val igDm = listOf("direct_thread_header", "message_list", "row_thread_composer_edittext", "thread_fragment_container")
    private val igStory = listOf("reel_viewer_root", "reel_viewer_media_container", "reel_viewer_texture_view", "reel_viewer_front_container")
    private val igComments = listOf("layout_comment_thread_edittext", "row_comment_textview_comment", "comment_composer_text_view", "comments_bottom_sheet")

    private fun instagram(pkg: String, root: AccessibilityNodeInfo): Covers {
        val clips = Nodes.firstId(root, pkg, igClips)
        val reelsTabSelected = Nodes.byId(root, pkg, "clips_tab").any { Nodes.selectedish(it) }
        if (Nodes.hasId(root, pkg, igDm) && clips == null) lastDmSeen[pkg] = now()

        if (clips != null || reelsTabSelected) {
            if (allowSingle(pkg, Store.opt("ig_allow_dm"))) return NONE
            if (reelsTabSelected) {
                if (!Nodes.click(Nodes.byId(root, pkg, "feed_tab").firstOrNull())) svc.back()
            } else {
                svc.back()
            }
            svc.blocked("Reels")
            return NONE
        }
        allowedSince.remove(pkg)

        if (Store.opt("ig_block_stories") && Nodes.hasId(root, pkg, igStory)) {
            svc.back(); svc.blocked("Stories"); return NONE
        }
        if (Store.opt("ig_block_comments") && Nodes.hasId(root, pkg, igComments)) {
            svc.back(); svc.blocked("comments"); return NONE
        }

        val tabs = Nodes.byId(root, pkg, "tab_bar").firstOrNull()?.let(Nodes::bounds)
        val onFeed = Nodes.byId(root, pkg, "feed_tab").any { Nodes.selectedish(it) }
        val list = Nodes.byId(root, pkg, "list").maxByOrNull { Nodes.bounds(it).height() }

        if (onFeed && list != null) {
            if (Store.opt("ig_block_feed")) {
                if (Store.choice("ig_feed_mode", "hide") == "dms") {
                    Nodes.click(Nodes.byId(root, pkg, "direct_tab").firstOrNull())
                    return NONE
                }
                val lb = Nodes.bounds(list)
                val tray = Nodes.byId(root, pkg, "reels_tray_container").firstOrNull()?.let(Nodes::bounds)
                val top = if (tray != null && tray.bottom > lb.top) tray.bottom else lb.top
                val bottom = tabs?.top?.coerceAtMost(lb.bottom) ?: lb.bottom
                return if (bottom > top) Covers(listOf(Rect(lb.left, top, lb.right, bottom)), touchable = true) else NONE
            }
            if (Store.opt("ig_hide_home_reels", true)) {
                val rows = rowsContaining(list) { n ->
                    val id = n.viewIdResourceName ?: ""
                    id.contains("clips") || REEL_WORD.containsMatchIn(n.contentDescription ?: "")
                }
                return Covers(rows, touchable = false)
            }
        }

        val onSearch = Nodes.byId(root, pkg, "search_tab").any { Nodes.selectedish(it) }
        if (onSearch && Store.opt("ig_hide_explore")) {
            val box = Nodes.firstId(root, pkg, listOf("action_bar_search_edit_text", "search_edit_text"))
            val typing = box != null && (box.isFocused || !box.text.isNullOrEmpty() && box.text.toString() != box.hintText?.toString())
            if (!typing) {
                val grid = Nodes.findAll(root) { it.isScrollable && it.isVisibleToUser }
                    .maxByOrNull { Nodes.bounds(it).height() }
                if (grid != null) {
                    val gb = Nodes.bounds(grid)
                    val top = maxOf(gb.top, box?.let { Nodes.bounds(it).bottom } ?: gb.top)
                    val bottom = tabs?.top?.coerceAtMost(gb.bottom) ?: gb.bottom
                    if (bottom > top) return Covers(listOf(Rect(gb.left, top, gb.right, bottom)), touchable = true)
                }
            }
        }
        return NONE
    }

    // YouTube

    private val ytShorts = listOf(
        "reel_recycler", "reel_player_page_container", "reel_watch_player", "reel_player_overlay_container",
        "reel_player_underlay", "shorts_container", "reel_watch_fragment_root",
    )

    private fun youtube(pkg: String, root: AccessibilityNodeInfo): Covers {
        val shorts = Nodes.firstId(root, pkg, ytShorts)
        if (shorts != null && Nodes.bounds(shorts).height() > svc.screenHeight() / 2) {
            if (allowSingle(pkg, Store.opt("yt_allow_link"), cameFromOutside = svc.justSwitchedFromOtherApp(pkg))) return NONE
            val shortsTab = Nodes.byLabel(root, Regex("(?i)^shorts$"))
            if (shortsTab != null && Nodes.selectedish(shortsTab)) {
                if (!Nodes.click(Nodes.byLabel(root, Regex("(?i)^home$")))) svc.back()
            } else {
                svc.back()
            }
            svc.blocked("Shorts")
            return NONE
        }
        allowedSince.remove(pkg)

        if (Store.opt("yt_hide_shelf")) {
            val list = Nodes.findAll(root) { it.isScrollable && it.isVisibleToUser }.maxByOrNull { Nodes.bounds(it).height() }
            if (list != null) {
                val rows = rowsContaining(list) { n ->
                    val id = n.viewIdResourceName ?: ""
                    id.contains("reel_shelf") || id.contains("reel_item") || Nodes.label(n).trim().equals("Shorts", true)
                }
                return Covers(rows, touchable = false)
            }
        }
        return NONE
    }

    // Facebook

    private fun facebook(pkg: String, root: AccessibilityNodeInfo): Covers {
        val reelsTab = Nodes.byLabel(root, Regex("(?i)^(reels|video)(,| tab).*"))
        val reelsHeader = Nodes.byLabel(root, Regex("(?i)^reels$"))
            ?.takeIf { Nodes.bounds(it).top < svc.screenHeight() / 6 }
        val homeTab = Nodes.byLabel(root, Regex("(?i)^home(,| tab).*"))
        val onHome = homeTab != null && Nodes.selectedish(homeTab)
        // a "Reels" header inside the home feed is a shelf, not the Reels viewer
        val onReels = (reelsTab != null && Nodes.selectedish(reelsTab)) || (reelsHeader != null && !onHome)
        if (onReels) {
            when (Store.choice("fb_redirect", "home")) {
                "home" -> if (!Nodes.click(Nodes.byLabel(root, Regex("(?i)^home(,| tab).*")))) svc.back()
                "close" -> svc.home()
                else -> svc.back()
            }
            svc.blocked("Reels")
            return NONE
        }
        if (Store.opt("fb_block_feed")) {
            if (onHome) {
                val target = when (Store.choice("fb_feed_target", "profile")) {
                    "notifications" -> Regex("(?i)^notifications(,| tab).*")
                    "menu" -> Regex("(?i)^menu(,| tab).*")
                    "friends" -> Regex("(?i)^friends(,| tab).*")
                    else -> Regex("(?i)^(profile|your profile)(,| tab).*")
                }
                Nodes.click(Nodes.byLabel(root, target))
            }
        }
        return NONE
    }

    // TikTok

    private fun tiktok(pkg: String, root: AccessibilityNodeInfo): Covers {
        // the For You label only exists on the home feed header (profiles also show "Following", so not that)
        val forYou = Nodes.byLabel(root, Regex("(?i)^for you$"))
            ?.takeIf { Nodes.bounds(it).top < svc.screenHeight() / 5 }
        if (Nodes.byLabel(root, Regex("(?i)^(send a message|message\\.\\.\\.|type a message)")) != null) lastDmSeen[pkg] = now()
        if (forYou == null) {
            // a single video opened from a chat: remember it so swiping to the next one gets blocked
            if (Nodes.byLabel(root, Regex("(?i)^(like|comments?|share)\\b.*")) != null) allowSingle(pkg, Store.opt("tt_allow_dm"))
            return NONE
        }
        if (!Nodes.click(Nodes.byLabel(root, Regex("(?i)^inbox$"))) &&
            !Nodes.click(Nodes.byLabel(root, Regex("(?i)^profile$")))
        ) svc.home()
        svc.blocked("the For You feed")
        return NONE
    }

    // Everything else: label or id signatures plus an escape route.

    private data class Sig(val ids: List<String> = emptyList(), val label: Regex? = null, val selected: Boolean = false, val topOnly: Boolean = false)

    private val sigs: Map<ReelsApp, Pair<List<Sig>, List<Regex>>> = mapOf(
        ReelsApp.REDDIT to (listOf(
            Sig(ids = listOf("video_pager", "fullscreen_video_pager", "immersive_video_container", "shorts_pager")),
            Sig(label = Regex("(?i)^(watch|videos)$"), selected = true),
        ) to listOf(Regex("(?i)^home$"))),
        ReelsApp.SNAPCHAT to (listOf(
            Sig(label = Regex("(?i)^spotlight$"), selected = true),
            Sig(label = Regex("(?i)^spotlight$"), topOnly = true),
        ) to listOf(Regex("(?i)^chat$"), Regex("(?i)^camera$"))),
        ReelsApp.LINKEDIN to (listOf(
            Sig(label = Regex("(?i)^video$"), selected = true),
            Sig(ids = listOf("media_pages_immersive_video_pager", "video_viewer_pager")),
        ) to listOf(Regex("(?i)^home$"))),
        ReelsApp.X to (listOf(
            Sig(ids = listOf("immersive_video_pager", "immersive_media_viewer", "video_tab_pager")),
            Sig(label = Regex("(?i)^(videos?|immersive video)$"), selected = true),
        ) to listOf(Regex("(?i)^home$"))),
        ReelsApp.INSTAGRAM_LITE to (listOf(Sig(label = Regex("(?i)^reels$"), selected = true), Sig(label = Regex("(?i)^reels$"), topOnly = true)) to listOf(Regex("(?i)^home$"))),
        ReelsApp.FACEBOOK_LITE to (listOf(Sig(label = Regex("(?i)^(reels|video)$"), selected = true), Sig(label = Regex("(?i)^reels$"), topOnly = true)) to listOf(Regex("(?i)^home$"))),
    )

    private fun generic(app: ReelsApp, pkg: String, root: AccessibilityNodeInfo): Covers {
        val (list, escapes) = sigs[app] ?: return NONE
        val hit = list.any { s ->
            val byId = s.ids.isNotEmpty() && Nodes.firstId(root, pkg, s.ids)?.let { Nodes.bounds(it).height() > svc.screenHeight() / 2 } == true
            val byLabel = s.label?.let { Nodes.byLabel(root, it) }?.let { n ->
                (!s.selected || Nodes.selectedish(n)) && (!s.topOnly || Nodes.bounds(n).top < svc.screenHeight() / 6)
            } == true
            byId || byLabel
        }
        if (!hit) return NONE
        if (escapes.none { Nodes.click(Nodes.byLabel(root, it)) }) svc.back()
        svc.blocked(app.surface)
        return NONE
    }

    /** Bounds of the direct children of [list] that contain a node matching [pred]. */
    private fun rowsContaining(list: AccessibilityNodeInfo, pred: (AccessibilityNodeInfo) -> Boolean): List<Rect> {
        val lb = Nodes.bounds(list)
        val out = mutableListOf<Rect>()
        for (i in 0 until list.childCount) {
            val row = list.getChild(i) ?: continue
            if (!row.isVisibleToUser) continue
            if (Nodes.find(row, 300, pred) != null) {
                val r = Nodes.bounds(row)
                r.intersect(lb)
                if (r.height() > 0) out += r
            }
        }
        return out
    }

    companion object {
        val NONE = Covers(emptyList(), false)
        private val REEL_WORD = Regex("(?i)\\breels?\\b")
    }
}
