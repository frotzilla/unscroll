package com.redwan.unscroll.service

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/** Small helpers for walking accessibility trees. */
object Nodes {

    /** Visible nodes with the given view id (without the package prefix). */
    fun byId(root: AccessibilityNodeInfo, pkg: String, id: String): List<AccessibilityNodeInfo> =
        runCatching { root.findAccessibilityNodeInfosByViewId("$pkg:id/$id") }.getOrNull()
            .orEmpty().filter { it.isVisibleToUser }

    fun firstId(root: AccessibilityNodeInfo, pkg: String, ids: List<String>): AccessibilityNodeInfo? {
        for (id in ids) byId(root, pkg, id).firstOrNull()?.let { return it }
        return null
    }

    fun hasId(root: AccessibilityNodeInfo, pkg: String, ids: List<String>) = firstId(root, pkg, ids) != null

    /** Depth first search, capped so a huge tree cannot stall the service. */
    fun find(root: AccessibilityNodeInfo, limit: Int = 1500, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var seen = 0
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty() && seen < limit) {
            val n = stack.removeLast()
            seen++
            if (pred(n)) return n
            for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let(stack::addLast)
        }
        return null
    }

    fun findAll(root: AccessibilityNodeInfo, limit: Int = 1500, pred: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        find(root, limit) { if (pred(it)) out += it; false }
        return out
    }

    fun label(n: AccessibilityNodeInfo): String = (n.contentDescription ?: n.text ?: "").toString()

    fun byLabel(root: AccessibilityNodeInfo, regex: Regex, limit: Int = 1500) =
        find(root, limit) { it.isVisibleToUser && regex.matches(label(it).trim()) }

    fun bounds(n: AccessibilityNodeInfo) = Rect().also { n.getBoundsInScreen(it) }

    /** Collects every visible text and description in the tree. */
    fun texts(root: AccessibilityNodeInfo, limit: Int = 800): List<String> =
        findAll(root, limit) { it.isVisibleToUser && (it.text != null || it.contentDescription != null) }
            .flatMap { listOfNotNull(it.text?.toString(), it.contentDescription?.toString()) }

    /** Clicks the node, or its nearest clickable ancestor. */
    fun click(n: AccessibilityNodeInfo?): Boolean {
        var cur = n
        var hops = 0
        while (cur != null && hops < 6) {
            if (cur.isClickable) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            cur = cur.parent
            hops++
        }
        return false
    }

    /** True if this node or any ancestor within a few hops is selected. */
    fun selectedish(n: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = n
        var hops = 0
        while (cur != null && hops < 3) {
            if (cur.isSelected || cur.isChecked) return true
            cur = cur.parent
            hops++
        }
        return false
    }

    /** Diagnostics: logs view ids, classes, and descriptions (never typed text) of the tree. */
    fun dump(tag: String, root: AccessibilityNodeInfo) {
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40) return
            val id = n.viewIdResourceName?.substringAfter(":id/") ?: ""
            val cls = n.className?.toString()?.substringAfterLast('.') ?: ""
            val desc = n.contentDescription?.toString()?.take(40) ?: ""
            val flags = buildString {
                if (n.isScrollable) append('S')
                if (n.isSelected) append('*')
                if (n.isClickable) append('C')
                if (!n.isVisibleToUser) append('h')
            }
            val b = bounds(n)
            if (id.isNotEmpty() || desc.isNotEmpty() || n.isScrollable)
                sb.append(" ".repeat(depth)).append("$cls #$id '$desc' [$flags] ${b.toShortString()}\n")
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        sb.lines().chunked(60).forEach { Log.d(tag, it.joinToString("\n")) }
    }
}
