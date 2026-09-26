package com.redwan.unscroll.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A scroll limit for one app. Scrolling counts as continuous while every [windowSec] seconds contain
 * at least [minScrolls] swipes; after [limitSec] of that, a check-in appears. The check-in can be
 * dismissed after [waitSec].
 */
data class ScrollRule(
    val pkg: String,
    val on: Boolean = false,
    val limitSec: Int = 10 * 60,
    val minScrolls: Int = 1,
    val windowSec: Int = 20,
    val waitSec: Int = 45,
    /** Minutes allowed per day, 0 for no allowance. */
    val dailyMin: Int = 0,
    /** Ask what the app is being opened for. */
    val askIntent: Boolean = false,
) {
    val active get() = on && limitSec > 0
    val any get() = active || dailyMin > 0 || askIntent

    fun toJson() = JSONObject()
        .put("pkg", pkg).put("on", on).put("limit", limitSec)
        .put("min", minScrolls).put("win", windowSec).put("wait", waitSec)
        .put("daily", dailyMin).put("ask", askIntent)

    companion object {
        val limitPresets = listOf(5, 10, 20)
        val waitPresets = listOf(15, 45, 90)

        fun fromJson(o: JSONObject) = ScrollRule(
            pkg = o.getString("pkg"),
            on = o.optBoolean("on", false),
            limitSec = o.optInt("limit", 600),
            minScrolls = o.optInt("min", 1),
            windowSec = o.optInt("win", 20),
            waitSec = o.optInt("wait", 45),
            dailyMin = o.optInt("daily", 0),
            askIntent = o.optBoolean("ask", false),
        )
    }
}

/**
 * A scheduled break: between [startMin] and [endMin] (minutes after midnight, may wrap past midnight)
 * on [days] (1 = Monday ... 7 = Sunday), blocking is relaxed for [apps] (empty means every app).
 */
data class BreakRule(
    val id: Long,
    val name: String,
    val days: Set<Int>,
    val startMin: Int,
    val endMin: Int,
    val apps: Set<String>,
    val relaxReels: Boolean = true,
    val relaxScroll: Boolean = true,
    val enabled: Boolean = true,
) {
    fun toJson() = JSONObject()
        .put("id", id).put("name", name)
        .put("days", JSONArray(days.toList()))
        .put("start", startMin).put("end", endMin)
        .put("apps", JSONArray(apps.toList()))
        .put("reels", relaxReels).put("scroll", relaxScroll)
        .put("on", enabled)

    companion object {
        fun fromJson(o: JSONObject) = BreakRule(
            id = o.getLong("id"),
            name = o.optString("name", "Break"),
            days = o.optJSONArray("days").ints().toSet(),
            startMin = o.optInt("start", 18 * 60),
            endMin = o.optInt("end", 21 * 60),
            apps = o.optJSONArray("apps").strings().toSet(),
            relaxReels = o.optBoolean("reels", true),
            relaxScroll = o.optBoolean("scroll", true),
            enabled = o.optBoolean("on", true),
        )
    }
}

fun JSONArray?.ints(): List<Int> = if (this == null) emptyList() else List(length()) { getInt(it) }
fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }
fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }
