package com.redwan.unscroll.data

/** Static reference data: supported apps, presets, and default copy. */
object Catalog {

    /** Short form video surfaces AntiReels knows how to block. */
    enum class ReelsApp(val key: String, val label: String, val pkg: String, val surface: String) {
        INSTAGRAM("ig", "Instagram", "com.instagram.android", "Reels"),
        FACEBOOK("fb", "Facebook", "com.facebook.katana", "Reels"),
        YOUTUBE("yt", "YouTube", "com.google.android.youtube", "Shorts"),
        REDDIT("rd", "Reddit", "com.reddit.frontpage", "video feed"),
        TIKTOK("tt", "TikTok", "com.zhiliaoapp.musically", "For You feed"),
        SNAPCHAT("sc", "Snapchat", "com.snapchat.android", "Spotlight"),
        LINKEDIN("li", "LinkedIn", "com.linkedin.android", "video feed"),
        X("x", "X", "com.twitter.android", "video feed"),
        INSTAGRAM_LITE("igl", "Instagram Lite", "com.instagram.lite", "Reels"),
        FACEBOOK_LITE("fbl", "Facebook Lite", "com.facebook.lite", "Reels");

        companion object {
            fun byPkg(pkg: String): ReelsApp? = entries.firstOrNull { it.pkg == pkg || it.altPkgs().contains(pkg) }
        }

        fun altPkgs(): List<String> = when (this) {
            TIKTOK -> listOf("com.ss.android.ugc.trill", "com.ss.android.ugc.aweme")
            else -> emptyList()
        }
    }

    /** Apps suggested at the top of the AntiScroll list. */
    val suggestedScrollApps = listOf(
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.twitter.android", "com.reddit.frontpage", "com.pinterest",
        "com.instagram.android", "com.google.android.youtube", "com.facebook.katana",
        "com.snapchat.android", "com.linkedin.android", "com.tumblr", "com.bereal.ft",
        "com.threads.android", "com.instagram.barcelona", "tv.twitch.android.app",
        "com.instagram.lite", "com.facebook.lite",
    )

    const val PAUSE_SENTENCE = "snooze unscroll anyway"

    /** Browsers whose address bar AntiSites can read. View ids without the package prefix. */
    val browsers: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("url_bar"),
        "com.chrome.beta" to listOf("url_bar"),
        "com.chrome.dev" to listOf("url_bar"),
        "com.vivaldi.browser" to listOf("url_bar"),
        "com.vivaldi.browser.snapshot" to listOf("url_bar"),
        "com.brave.browser" to listOf("url_bar"),
        "com.microsoft.emmx" to listOf("url_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar"),
        "org.chromium.chrome" to listOf("url_bar"),
        "com.opera.browser" to listOf("url_field"),
        "com.opera.mini.native" to listOf("url_field"),
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view"),
        "org.mozilla.fenix" to listOf("mozac_browser_toolbar_url_view"),
        "org.mozilla.focus" to listOf("display_url", "mozac_browser_toolbar_url_view"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text", "custom_url_bar"),
        "com.sec.android.app.sbrowser.beta" to listOf("location_bar_edit_text"),
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
        "com.ecosia.android" to listOf("url_bar"),
    )

    /** Well known adult domains. Matching also covers every subdomain. */
    val adultDomains = setOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com", "youporn.com",
        "tube8.com", "spankbang.com", "eporner.com", "tnaflix.com", "beeg.com", "motherless.com",
        "brazzers.com", "onlyfans.com", "fansly.com", "chaturbate.com", "stripchat.com",
        "bongacams.com", "cam4.com", "livejasmin.com", "myfreecams.com", "camsoda.com",
        "rule34.xxx", "e621.net", "nhentai.net", "hentaihaven.xxx", "hanime.tv", "gelbooru.com",
        "e-hentai.org", "exhentai.org", "porntrex.com", "hqporner.com", "txxx.com", "upornia.com",
        "sxyprn.com", "porn.com", "4tube.com", "fapello.com", "thothub.to", "erome.com",
        "redgifs.com", "literotica.com", "iwara.tv", "f95zone.to", "nudostar.com", "clips4sale.com",
        "manyvids.com", "xhamsterlive.com", "youjizz.com", "drtuber.com", "sunporno.com",
        "pornpics.com", "imagefap.com", "sex.com", "porngo.com", "daftsex.com", "ixxx.com",
    )

    /** Host keywords that mark a site as adult even if it is not on the list. */
    val adultKeywords = listOf("porn", "xxx", "hentai", "xvideo", "xhamster", "nsfw", "camgirl", "sexcam", "onlyfan", "rule34")
}
