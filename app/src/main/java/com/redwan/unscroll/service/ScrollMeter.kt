package com.redwan.unscroll.service

import com.redwan.unscroll.data.ScrollRule

/**
 * Detects continuous scrolling. A session keeps going while every window of [ScrollRule.windowSec]
 * contains at least [ScrollRule.minScrolls] scrolls. Once a session lasts [ScrollRule.triggerSec],
 * [onScroll] reports a trigger and the session starts over.
 */
class ScrollMeter {
    private class Session {
        var start = 0L
        var lastQualified = 0L
        var lastScroll = 0L
        val stamps = ArrayDeque<Long>()
    }

    private val sessions = HashMap<String, Session>()

    /** Returns true when the scroll limit for [rule] was just reached. */
    fun onScroll(rule: ScrollRule, now: Long = System.currentTimeMillis()): Boolean {
        if (!rule.active || rule.triggerSec <= 0) return false
        val s = sessions.getOrPut(rule.pkg) { Session() }
        // one fling fires a burst of scroll events; count it once
        if (now - s.lastScroll < GESTURE_MS) return false
        s.lastScroll = now

        val windowMs = rule.windowSec * 1000L
        s.stamps.addLast(now)
        while (s.stamps.isNotEmpty() && now - s.stamps.first() > windowMs) s.stamps.removeFirst()

        if (s.stamps.size >= rule.minScrolls.coerceAtLeast(1)) {
            if (s.start == 0L || now - s.lastQualified > windowMs) s.start = now
            s.lastQualified = now
        }
        if (s.start != 0L && now - s.start >= rule.triggerSec * 1000L) {
            reset(rule.pkg)
            return true
        }
        return false
    }

    /** Minutes of continuous scrolling so far in [pkg]. */
    fun minutes(pkg: String, now: Long = System.currentTimeMillis()): Int {
        val s = sessions[pkg] ?: return 0
        return if (s.start == 0L) 0 else ((now - s.start) / 60_000L).toInt()
    }

    fun reset(pkg: String) {
        sessions.remove(pkg)
    }

    companion object {
        const val GESTURE_MS = 450L
    }
}
