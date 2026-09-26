package com.redwan.unscroll.data

import org.json.JSONArray
import org.json.JSONObject

/** How eagerly AntiScroll interrupts a scrolling session. */
enum class ScrollMode(val label: String, val windowSec: Int, val minScrolls: Int, val triggerSec: Int, val blurb: String) {
    OFF("OFF", 0, 0, 0,
        "AntiScroll is off for this app. Scroll as much as you want."),
    CHILL("CHILL", 30, 1, 15 * 60,
        "CHILL is the most relaxed mode. The popup shows after about 15 minutes of continuous scrolling.\n\nGood for when you are just hanging out and do not mind a bit more time on your phone."),
    NORMAL("NORMAL", 20, 1, 8 * 60,
        "NORMAL balances restriction and freedom. The popup shows after about 8 minutes of continuous scrolling.\n\nGood for daily use when you want to stay connected without losing hours to the feed."),
    STRICT("STRICT", 20, 1, 3 * 60,
        "STRICT is the most rigorous mode. The popup shows after about 3 minutes of continuous scrolling."),
    CUSTOM("CUSTOM", 0, 0, 0, "");
}

/** Per app AntiScroll configuration. */
data class ScrollRule(
    val pkg: String,
    val mode: ScrollMode = ScrollMode.OFF,
    val customMinScrolls: Int = 1,
    val customWindowSec: Int = 20,
    val customTriggerSec: Int = 180,
    val popupTimeoutSec: Int = 60,
) {
    val windowSec get() = if (mode == ScrollMode.CUSTOM) customWindowSec else mode.windowSec
    val minScrolls get() = if (mode == ScrollMode.CUSTOM) customMinScrolls else mode.minScrolls
    val triggerSec get() = if (mode == ScrollMode.CUSTOM) customTriggerSec else mode.triggerSec
    val active get() = mode != ScrollMode.OFF

    fun toJson() = JSONObject()
        .put("pkg", pkg).put("mode", mode.name)
        .put("min", customMinScrolls).put("win", customWindowSec)
        .put("trig", customTriggerSec).put("pop", popupTimeoutSec)

    companion object {
        fun fromJson(o: JSONObject) = ScrollRule(
            pkg = o.getString("pkg"),
            mode = runCatching { ScrollMode.valueOf(o.optString("mode")) }.getOrDefault(ScrollMode.OFF),
            customMinScrolls = o.optInt("min", 1),
            customWindowSec = o.optInt("win", 20),
            customTriggerSec = o.optInt("trig", 180),
            popupTimeoutSec = o.optInt("pop", 60),
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

/** A trigger the user named, and what they plan to do instead. */
data class TriggerPlan(val trigger: String, val instead: String) {
    fun toJson() = JSONObject().put("t", trigger).put("i", instead)

    companion object {
        fun fromJson(o: JSONObject) = TriggerPlan(o.optString("t"), o.optString("i"))
    }
}

fun JSONArray?.ints(): List<Int> = if (this == null) emptyList() else List(length()) { getInt(it) }
fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }
fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }
