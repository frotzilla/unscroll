package com.redwan.unscroll.service

import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import com.redwan.unscroll.data.Catalog
import com.redwan.unscroll.data.Store

/** AntiSites: reads the browser address bar and leaves blocked sites for the safe page. */
class SiteGuard(private val svc: GuardService) {
    private var lastHost = ""
    private var lastAt = 0L

    fun isBrowser(pkg: String) = pkg in Catalog.browsers

    fun check(pkg: String, root: AccessibilityNodeInfo) {
        val custom = Store.blockedSites
        if (!Store.adultBlock && custom.isEmpty()) return
        val bar = Nodes.firstId(root, pkg, Catalog.browsers[pkg].orEmpty()) ?: return
        if (bar.isFocused) return // the user is typing, not visiting
        val host = hostOf(bar.text?.toString() ?: return) ?: return
        if (!blocked(host, custom)) return

        val now = System.currentTimeMillis()
        if (host == lastHost && now - lastAt < 3000) return
        lastHost = host
        lastAt = now

        svc.back()
        Store.bump(Store.Counter.SITES)
        svc.toast("Unscroll blocked $host")
        val safe = Store.safeUrl.trim()
        if (safe.isNotEmpty()) {
            svc.mainHandler.postDelayed({
                runCatching {
                    svc.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safe)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }, 350)
        }
    }

    companion object {
        fun hostOf(raw: String): String? {
            var s = raw.trim().lowercase()
            if (s.isEmpty() || s.contains(' ')) return null
            s = s.substringAfter("://")
            s = s.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
            s = s.removePrefix("www.").removePrefix("m.")
            return s.takeIf { it.contains('.') }
        }

        /** Entries with a dot match that domain and its subdomains; plain words match anywhere in the host. */
        fun blocked(host: String, custom: Set<String>): Boolean {
            fun matches(entry: String): Boolean {
                val e = entry.trim().lowercase().removePrefix("www.")
                if (e.isEmpty()) return false
                return if (e.contains('.')) host == e || host.endsWith(".$e") else host.contains(e)
            }
            if (custom.any(::matches)) return true
            if (!Store.adultBlock) return false
            return Catalog.adultDomains.any(::matches) || Catalog.adultKeywords.any { host.contains(it) }
        }
    }
}
