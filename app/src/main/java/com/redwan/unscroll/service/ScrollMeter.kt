package com.redwan.unscroll.service

import com.redwan.unscroll.data.ScrollRule

/**
 * Detects continuous scrolling. A session keeps going while every window of [ScrollRule.windowSec]
 * contains at least [ScrollRule.minScrolls] scrolls. Once a session lasts [ScrollRule.limitSec],
 * [onScroll] reports it and the session starts over.
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
        if (!rule.active) return false
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
        if (s.start != 0L && now - s.start >= rule.limitSec * 1000L) {
            reset(rule.pkg)
            return true
        }
        return false
    }

    fun reset(pkg: String) {
        sessions.remove(pkg)
    }

    companion object {
        const val GESTURE_MS = 450L
    }
}
