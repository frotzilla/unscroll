package com.redwan.unscroll.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * All persisted state. Backed by SharedPreferences so the accessibility service (same process)
 * always reads the latest values. [rev] bumps on every write so Compose screens recompose.
 */
object Store {
    private lateinit var prefs: SharedPreferences
    val rev = mutableIntStateOf(0)

    /**
     * Every read goes through here and touches [rev], so any composable that shows a setting
     * redraws when a setting changes, even deep inside cards Compose would otherwise skip.
     */
    private val sp: SharedPreferences
        get() {
            rev.intValue
            return prefs
        }

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("unscroll", Context.MODE_PRIVATE)
        if (!sp.contains("streak_start_day")) sp.edit().putLong("streak_start_day", today()).apply()
    }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        rev.intValue++
    }

    private fun bool(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun setBool(key: String, v: Boolean) = edit { putBoolean(key, v) }
    private fun str(key: String, def: String) = sp.getString(key, def) ?: def
    private fun setStr(key: String, v: String) = edit { putString(key, v) }

    fun today(): Long = LocalDate.now().toEpochDay()

    // Onboarding
    var onboarded: Boolean
        get() = bool("onboarded", false)
        set(v) = setBool("onboarded", v)

    // AntiReels
    var reelsMaster: Boolean
        get() = bool("reels_master", true)
        set(v) = setBool("reels_master", v)

    fun reelsApp(app: Catalog.ReelsApp) = bool("reels_app_${app.key}", true)
    fun setReelsApp(app: Catalog.ReelsApp, v: Boolean) = setBool("reels_app_${app.key}", v)

    /** Boolean sub option of an AntiReels app, e.g. opt("ig_block_stories"). */
    fun opt(key: String, def: Boolean = false) = bool("opt_$key", def)
    fun setOpt(key: String, v: Boolean) = setBool("opt_$key", v)
    fun choice(key: String, def: String) = str("choice_$key", def)
    fun setChoice(key: String, v: String) = setStr("choice_$key", v)

    // AntiScroll
    fun scrollRules(): Map<String, ScrollRule> =
        JSONArray(str("scroll_rules", "[]")).objects().map(ScrollRule::fromJson).associateBy { it.pkg }

    fun scrollRule(pkg: String): ScrollRule = scrollRules()[pkg] ?: ScrollRule(pkg)

    fun putScrollRule(rule: ScrollRule) {
        val all = scrollRules().toMutableMap()
        all[rule.pkg] = rule
        setStr("scroll_rules", JSONArray(all.values.map { it.toJson() }).toString())
    }

    var cooldownOn: Boolean
        get() = bool("cooldown_on", false)
        set(v) = setBool("cooldown_on", v)
    var cooldownMin: Int
        get() = sp.getInt("cooldown_min", 10)
        set(v) = edit { putInt("cooldown_min", v) }
    var cooldownExtraApps: Set<String>
        get() = sp.getStringSet("cooldown_extra", emptySet())!!.toSet()
        set(v) = edit { putStringSet("cooldown_extra", v) }

    /** Runtime cooldown state, written by the service. */
    var cooldownUntil: Long
        get() = sp.getLong("cooldown_until", 0)
        set(v) = edit { putLong("cooldown_until", v) }

    // Pause
    var pauseUntil: Long
        get() = sp.getLong("pause_until", 0)
        set(v) = edit { putLong("pause_until", v) }
    val paused get() = System.currentTimeMillis() < pauseUntil

    fun pausesToday(): Int = if (sp.getLong("pauses_day", -1) == today()) sp.getInt("pauses_count", 0) else 0

    fun startPause(minutes: Int) {
        val count = pausesToday() + 1
        edit {
            putLong("pause_until", System.currentTimeMillis() + minutes * 60_000L)
            putLong("pauses_day", today())
            putInt("pauses_count", count)
            putLong("last_pause_day", today())
        }
        streak() // refresh record before the reset takes effect
    }

    fun endPause() = edit { putLong("pause_until", 0) }

    // Streak of days without pausing
    data class Streak(val current: Long, val record: Long)

    fun streak(): Streak {
        val last = sp.getLong("last_pause_day", -1)
        val start = if (last >= 0) last else sp.getLong("streak_start_day", today())
        val current = (today() - start).coerceAtLeast(0)
        var record = sp.getLong("streak_record", 0)
        if (current > record) {
            record = current
            sp.edit().putLong("streak_record", record).apply()
        }
        return Streak(current, record)
    }

    // Settings
    var antiPause: Boolean
        get() = bool("anti_pause", false)
        set(v) = setBool("anti_pause", v)
    var hideBreaks: Boolean
        get() = bool("hide_breaks", false)
        set(v) = setBool("hide_breaks", v)
    var hidePause: Boolean
        get() = bool("hide_pause", false)
        set(v) = setBool("hide_pause", v)
    var uninstallProtect: Boolean
        get() = bool("uninstall_protect", false)
        set(v) = setBool("uninstall_protect", v)

    var messages: List<String>
        get() = if (sp.contains("messages")) JSONArray(str("messages", "[]")).strings() else Catalog.defaultMessages
        set(v) = setStr("messages", JSONArray(v).toString())

    // Password
    val hasPin get() = sp.contains("pin_hash")

    fun setPin(pin: String?) {
        if (pin == null) {
            edit { remove("pin_hash"); remove("pin_salt") }
            return
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        edit { putString("pin_salt", salt); putString("pin_hash", hash(salt, pin)) }
    }

    fun checkPin(pin: String) = hasPin && hash(str("pin_salt", ""), pin) == str("pin_hash", "")

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    // AntiSites
    var adultBlock: Boolean
        get() = bool("adult_block", false)
        set(v) = setBool("adult_block", v)
    var blockedSites: Set<String>
        get() = sp.getStringSet("blocked_sites", emptySet())!!.toSet()
        set(v) = edit { putStringSet("blocked_sites", v) }
    var safeUrl: String
        get() = str("safe_url", "https://en.wikipedia.org/wiki/Special:Random")
        set(v) = setStr("safe_url", v)

    // Tips
    var why: String
        get() = str("why_text", "")
        set(v) = setStr("why_text", v)
    var whyCosts: Set<String>
        get() = sp.getStringSet("why_costs", emptySet())!!.toSet()
        set(v) = edit { putStringSet("why_costs", v) }
    var whyFuture: String
        get() = str("why_future", "")
        set(v) = setStr("why_future", v)
    var commitmentIdx: Int
        get() = sp.getInt("commit_idx", -1)
        set(v) = edit { putInt("commit_idx", v) }
    var commitmentCustom: String
        get() = str("commit_custom", "")
        set(v) = setStr("commit_custom", v)
    var triggerPlans: List<TriggerPlan>
        get() = JSONArray(str("trigger_plans", "[]")).objects().map(TriggerPlan::fromJson)
        set(v) = setStr("trigger_plans", JSONArray(v.map { it.toJson() }).toString())
    var environment: Set<String>
        get() = sp.getStringSet("environment", emptySet())!!.toSet()
        set(v) = edit { putStringSet("environment", v) }

    fun commitmentText(): String? = when (val i = commitmentIdx) {
        -1 -> null
        Catalog.commitments.lastIndex -> commitmentCustom.ifBlank { null }
        else -> Catalog.commitments.getOrNull(i)?.title
    }

    fun tipsProgress(): Int = listOf(
        why.isNotBlank() || whyCosts.isNotEmpty(),
        commitmentText() != null,
        triggerPlans.isNotEmpty(),
        environment.isNotEmpty(),
    ).count { it }

    // Breaks
    var breaks: List<BreakRule>
        get() = JSONArray(str("breaks", "[]")).objects().map(BreakRule::fromJson)
        set(v) = setStr("breaks", JSONArray(v.map { it.toJson() }).toString())

    /** The break relaxing [pkg] right now, if any. */
    fun activeBreak(pkg: String, now: LocalDateTime = LocalDateTime.now(), list: List<BreakRule> = breaks): BreakRule? {
        val dow = now.dayOfWeek.value
        val minute = now.hour * 60 + now.minute
        return list.firstOrNull { b ->
            if (!b.enabled || (b.apps.isNotEmpty() && pkg !in b.apps)) return@firstOrNull false
            if (b.startMin <= b.endMin) {
                dow in b.days && minute in b.startMin until b.endMin
            } else {
                // Wraps past midnight: the late part belongs to the start day, the early part to the previous day.
                val prev = if (dow == 1) 7 else dow - 1
                (dow in b.days && minute >= b.startMin) || (prev in b.days && minute < b.endMin)
            }
        }
    }

    // Counters shown in Insights
    enum class Counter { REELS, POPUPS, SITES, COOLDOWNS }

    fun bump(c: Counter) {
        val all = JSONObject(str("counters", "{}"))
        val day = all.optJSONObject(today().toString()) ?: JSONObject()
        day.put(c.name, day.optInt(c.name) + 1)
        all.put(today().toString(), day)
        // keep about two months of history
        val cutoff = today() - 60
        all.keys().asSequence().toList().filter { (it.toLongOrNull() ?: 0) < cutoff }.forEach { all.remove(it) }
        sp.edit().putString("counters", all.toString()).apply()
        rev.intValue++
    }

    fun counter(c: Counter, days: Int): Int {
        val all = JSONObject(str("counters", "{}"))
        return (0 until days).sumOf { all.optJSONObject((today() - it).toString())?.optInt(c.name) ?: 0 }
    }

    /** Logs view trees of the foreground app to logcat, for tuning detection. */
    var diagnostics: Boolean
        get() = bool("diag", false)
        set(v) = setBool("diag", v)

    // Service health
    var serviceExpected: Boolean
        get() = bool("svc_expected", false)
        set(v) = setBool("svc_expected", v)
}
