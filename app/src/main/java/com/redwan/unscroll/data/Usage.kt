package com.redwan.unscroll.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

/** Screen time from UsageStatsManager, split per day and per app. */
object Usage {

    fun granted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Foreground milliseconds per app for each of the last [days] days, oldest first. */
    data class Report(val days: List<LocalDate>, val perDay: List<Map<String, Long>>) {
        val totalPerDay get() = perDay.map { it.values.sum() }
        val perApp: Map<String, Long>
            get() = perDay.flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
    }

    fun report(ctx: Context, days: Int): Report {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val dates = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val maps = dates.map { HashMap<String, Long>() }
        val start = dates.first().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        val home = launcherPackages(ctx)

        val events = usm.queryEvents(start, end)
        val ev = UsageEvents.Event()
        val openSince = HashMap<String, Long>()

        fun add(pkg: String, from: Long, to: Long) {
            if (pkg in home || pkg == ctx.packageName || to <= from) return
            var s = from
            while (s < to) {
                val d = java.time.Instant.ofEpochMilli(s).atZone(zone).toLocalDate()
                val dayEnd = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val e = minOf(to, dayEnd)
                val idx = dates.indexOf(d)
                if (idx >= 0) maps[idx].merge(pkg, e - s, Long::plus)
                s = e
            }
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> openSince[ev.packageName] = ev.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    openSince.remove(ev.packageName)?.let { add(ev.packageName, it, ev.timeStamp) }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    openSince.forEach { (p, t) -> add(p, t, ev.timeStamp) }
                    openSince.clear()
                }
            }
        }
        openSince.forEach { (p, t) -> add(p, t, end) }
        return Report(dates, maps)
    }

    private fun launcherPackages(ctx: Context): Set<String> {
        val i = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
        return ctx.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toSet()
    }

    fun fmt(ms: Long): String {
        val m = ms / 60_000
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }
}
