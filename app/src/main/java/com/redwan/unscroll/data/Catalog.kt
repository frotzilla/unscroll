package com.redwan.unscroll.data

/** Static reference data: supported apps, presets, and default copy. */
object Catalog {

    /** Short form video surfaces AntiReels knows how to block. */
    enum class ReelsApp(val key: String, val label: String, val pkg: String, val surface: String) {
        INSTAGRAM("ig", "Instagram", "com.instagram.android", "Reels"),
        FACEBOOK("fb", "Facebook", "com.facebook.katana", "Reels"),
        YOUTUBE("yt", "YouTube", "com.google.android.youtube", "Shorts"),
        REDDIT("rd", "Reddit", "com.reddit.frontpage", "the video feed"),
        TIKTOK("tt", "TikTok", "com.zhiliaoapp.musically", "the For You feed"),
        SNAPCHAT("sc", "Snapchat", "com.snapchat.android", "Spotlight"),
        LINKEDIN("li", "LinkedIn", "com.linkedin.android", "the video feed"),
        X("x", "X", "com.twitter.android", "the video feed"),
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

    val defaultMessages = listOf(
        "Is this what you picked up your phone for?",
        "You can close this and still know everything that matters.",
        "The feed does not end. You get to.",
        "Take three slow breaths before you decide.",
        "What would you rather be doing right now?",
        "Nobody will remember this video tomorrow. Future you will remember the time.",
        "You opened this app for a reason. Did you find it?",
        "Stand up, stretch, drink some water.",
        "Your attention is the product here. Spend it on purpose.",
        "Boredom is where your own ideas start.",
    )

    const val PAUSE_SENTENCE = "I am choosing to pause my protection and I know the feed is designed to keep me here"

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

    val costs = listOf("Time", "Sleep", "Focus", "Mood and anxiety", "Relationships", "Goals and hobbies", "Presence with family", "Self respect")

    data class Commitment(val title: String, val detail: String)

    val commitments = listOf(
        Commitment("Cut my short video time right down", "Keep a small daily limit and hold it."),
        Commitment("Quit short form video for good", "No Reels, Shorts, or TikTok. Not at all."),
        Commitment("Step away from social media", "Stop using the social feeds entirely."),
        Commitment("Watch short videos only at a set time", "Pick one exact window and keep it small."),
        Commitment("Only reels people send me in DMs", "From people you know. Nothing from the feed."),
        Commitment("Something else", "Your own rule, in your own words."),
    )

    val triggers = listOf(
        "First thing in the morning", "As soon as I unlock my phone", "The moment I sit down at home",
        "Bored or restless", "Tired on the couch after work", "In bed at night",
        "Waiting in line", "On the toilet", "When I should be working", "After a notification",
        "When I feel anxious or low", "During meals", "While a video or show plays",
    )

    val replacements = listOf(
        "Read a few pages", "Go for a short walk", "Text a friend instead", "Stretch for two minutes",
        "Drink a glass of water", "Write one line in a journal", "Put the phone in another room",
        "Listen to a podcast or album", "Tidy one small thing", "Just sit and breathe",
    )

    val environment = listOf(
        "Put my phone in grayscale",
        "Remove tempting apps from my home screen",
        "No phone in the first and last hour of the day",
        "Charge my phone outside the bedroom",
        "Turn off the notifications I do not need",
        "Log out of the apps I scroll most",
        "Keep a book or hobby within reach",
    )

    data class Lesson(val title: String, val body: String)

    val brainLessons = listOf(
        Lesson("Variable rewards",
            "Every swipe might bring something great, or might not. That unpredictability is the same pattern slot machines use, and it is the strongest known way to keep a behaviour going. You are not weak. The feed is tuned against you."),
        Lesson("No natural stopping point",
            "A book has chapters and a show has credits. An infinite feed removes every cue your brain uses to decide it is done, so the only stop left is the one you add yourself."),
        Lesson("Short loops train short attention",
            "Fifteen second clips reward you for switching quickly. After a while slower things, like reading or a long conversation, start to feel harder than they really are."),
        Lesson("Habits run on triggers",
            "Most scrolling is not a decision. A cue (boredom, a spare minute, unlocking the phone) starts a routine that pays off with a small reward. Change what happens after the cue and the habit weakens."),
        Lesson("Friction beats willpower",
            "Willpower is a limited resource and worst exactly when you are tired. Adding a few seconds of friction, like a popup or a missing icon, is often enough for the thinking part of your brain to catch up."),
        Lesson("Urges pass",
            "An urge peaks and fades within a few minutes if you do not feed it. Every time you let one pass, the next one is a little weaker."),
    )
}
