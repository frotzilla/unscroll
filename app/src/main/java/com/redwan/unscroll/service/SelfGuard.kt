package com.redwan.unscroll.service

import android.view.accessibility.AccessibilityNodeInfo
import com.redwan.unscroll.R
import com.redwan.unscroll.data.Store

/**
 * Uninstall protection: while it is on, leaves the system screens that would uninstall, force stop,
 * clear, or switch off Unscroll. The switch to turn this off lives inside the app (behind the PIN).
 */
class SelfGuard(private val svc: GuardService) {
    private val guardedPkgs = setOf(
        "com.android.settings", "com.samsung.accessibility", "com.google.android.packageinstaller",
        "com.android.packageinstaller", "com.samsung.android.packageinstaller", "com.google.android.permissioncontroller",
        "com.miui.securitycenter", "com.coloros.safecenter", "com.oplus.safecenter",
    )
    private val appName by lazy { svc.getString(R.string.app_name) }
    private val serviceName by lazy { svc.getString(R.string.service_label) }

    fun watches(pkg: String) = pkg in guardedPkgs

    fun check(root: AccessibilityNodeInfo): Boolean {
        if (!Store.uninstallProtect) return false
        val texts = Nodes.texts(root)
        val lower = texts.map { it.lowercase() }
        val mentionsApp = texts.any { it.trim() == appName || it.contains("$appName?") || it.contains("\"$appName\"") }
        val mentionsService = texts.any { it.contains(serviceName) }

        val appInfo = mentionsApp && lower.any { t -> DANGER.any { t.contains(it) } }
        val uninstallDialog = mentionsApp && lower.any { it.contains("uninstall") }
        val serviceToggle = mentionsService && lower.any { t ->
            t.startsWith("use ") || t.startsWith("stop ") || t.startsWith("turn off") || t.contains("shortcut")
        }
        val adminPage = mentionsApp && lower.any { it.contains("deactivate") || it.contains("device admin") }

        if (appInfo || uninstallDialog || serviceToggle || adminPage) {
            svc.home()
            svc.toast("$appName is protected. Turn off uninstall protection inside the app first.")
            return true
        }
        return false
    }

    companion object {
        private val DANGER = listOf("uninstall", "force stop", "clear data", "clear storage", "disable")
    }
}
