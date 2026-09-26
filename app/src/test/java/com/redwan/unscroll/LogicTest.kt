package com.redwan.unscroll

import com.redwan.unscroll.data.BreakRule
import com.redwan.unscroll.data.ScrollMode
import com.redwan.unscroll.data.ScrollRule
import com.redwan.unscroll.data.Store
import com.redwan.unscroll.service.ScrollMeter
import com.redwan.unscroll.service.SiteGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class LogicTest {

    @Test
    fun strictTriggersAfterThreeMinutesOfSteadyScrolling() {
        val m = ScrollMeter()
        val rule = ScrollRule("app", ScrollMode.STRICT)
        var t = 1_000_000L
        var fired = -1L
        // one scroll every 10 seconds, well inside the 20 second window
        while (t < 1_000_000L + 5 * 60_000L) {
            if (m.onScroll(rule, t)) { fired = t; break }
            t += 10_000
        }
        assertEquals(1_000_000L + 180_000L, fired)
    }

    @Test
    fun aGapLongerThanTheWindowStartsANewSession() {
        val m = ScrollMeter()
        val rule = ScrollRule("app", ScrollMode.STRICT)
        var t = 0L
        repeat(15) { assertFalse(m.onScroll(rule, t)); t += 10_000 } // 150s of scrolling
        t += 30_000 // pause longer than the 20s window
        repeat(15) { assertFalse(m.onScroll(rule, t)); t += 10_000 } // another 150s, never 180 in a row
    }

    @Test
    fun burstsFromOneFlingCountOnce() {
        val m = ScrollMeter()
        val rule = ScrollRule("app", ScrollMode.CUSTOM, customMinScrolls = 3, customWindowSec = 10, customTriggerSec = 10)
        // 20 events within 200ms is one gesture, so the 3 per window minimum is never met
        repeat(20) { assertFalse(m.onScroll(rule, 5_000L + it * 10)) }
    }

    @Test
    fun offModeNeverTriggers() {
        val m = ScrollMeter()
        val rule = ScrollRule("app", ScrollMode.OFF)
        repeat(1000) { assertFalse(m.onScroll(rule, it * 5_000L)) }
    }

    @Test
    fun hostParsing() {
        assertEquals("pornhub.com", SiteGuard.hostOf("https://www.pornhub.com/view?x=1"))
        assertEquals("reddit.com", SiteGuard.hostOf("m.reddit.com/r/all"))
        assertEquals("news.ycombinator.com", SiteGuard.hostOf("news.ycombinator.com"))
        assertNull(SiteGuard.hostOf("search terms with spaces"))
        assertNull(SiteGuard.hostOf("localhost"))
    }

    @Test
    fun breaksHandleDaysAndMidnight() {
        val fri = LocalDateTime.of(2026, 9, 25, 20, 0) // a Friday
        val evening = BreakRule(1, "Evening", setOf(5), 19 * 60, 22 * 60, emptySet())
        assertNotNull(Store.activeBreak("x", fri, listOf(evening)))
        assertNull(Store.activeBreak("x", fri.withHour(22), listOf(evening)))

        val late = BreakRule(2, "Late", setOf(5), 23 * 60, 1 * 60, setOf("com.a"))
        assertNotNull(Store.activeBreak("com.a", fri.withHour(23).withMinute(30), listOf(late)))
        assertNotNull(Store.activeBreak("com.a", fri.plusDays(1).withHour(0).withMinute(30), listOf(late)))
        assertNull(Store.activeBreak("com.a", fri.plusDays(1).withHour(23).withMinute(30), listOf(late)))
        assertNull(Store.activeBreak("com.b", fri.withHour(23).withMinute(30), listOf(late)))
        assertTrue(Store.activeBreak("com.a", fri.withHour(23).withMinute(30), listOf(late.copy(enabled = false))) == null)
    }
}
