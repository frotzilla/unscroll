# Unscroll

An Android app that blocks Reels and Shorts, interrupts doomscrolling, and helps you stick to your own rules. Everything runs on the phone through an accessibility service. No accounts, no network, no paywall.

## Features

**AntiReels**
- Blocks Instagram Reels, Facebook Reels, YouTube Shorts, the TikTok For You feed, Reddit's video feed, Snapchat Spotlight, LinkedIn and X video feeds, Instagram Lite, and Facebook Lite. Each app can be switched on or off.
- Instagram: hide reels mixed into the home feed, block the main feed (cover it below Stories, or jump straight to DMs), allow a reel sent in DMs while blocking the swipe to the next one, block Stories, block comments, and hide the Explore grid while search keeps working.
- Facebook: choose where the Reels tab sends you, allow reels sent in Messenger (the next reel stays blocked), and block the main feed in favour of Profile, Notifications, Friends, or Menu.
- YouTube: allow a Short opened from a link, and hide Shorts shelves.
- TikTok: allow videos opened from a chat.

**AntiScroll**
- Pick any installed app and a mode: Chill (about 15 min), Normal (about 8 min), Strict (about 3 min), or Custom (minimum scrolls per window, window length, and trigger time).
- A full screen popup with a rotating reminder and your own "why". Close is always available; Keep scrolling unlocks after the popup timeout (30, 60, 120 s, or custom).
- Cross app cooldown: hitting a limit locks every scroll app, plus any extra apps you add (browsers, for example), for a set time.

**Protection**
- Pause for 1, 5, 15 minutes or a custom time, with a streak of days without pausing.
- Anti pause protection: after two pauses in a day, pausing again needs a typed sentence.
- Hide the pause button.
- PIN lock for the whole app.
- Uninstall protection: leaves app info, uninstall, force stop, device admin, and accessibility toggle screens for Unscroll, and adds a device admin step.
- Adult site blocking and your own blocked sites list in Chrome, Vivaldi, Brave, Edge, Firefox, Samsung Internet, Opera, DuckDuckGo, Kiwi, and Ecosia, with a safe page redirect.

**Breaks**
- Schedule times (including overnight) when AntiReels and/or AntiScroll relax, for every app or chosen apps.

**Insights**
- Screen time per day and per app (today, 7 days, 30 days) from usage access, plus counts of reels blocked, popups, sites blocked, and cooldowns.

**Tips**
- Your why (reasons, costs, and a picture of six months from now), your commitment, your triggers with a planned replacement for each, and environment tactics. Also a short "how your brain works" section.
- A home screen widget that shows your why and your rule.

**Troubleshooting**
- Service health, battery restriction fixes (with Samsung steps), restarting the service, and a diagnostics log for tuning detection.

## Build

Needs JDK 17 and the Android SDK (platform 34).

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then open Unscroll and follow the setup steps. The accessibility service is listed as "Unscroll protection".

## Tuning detection

Other apps change their screens often. Detection lives in `service/ReelsGuard.kt`. Turn on **Troubleshooting > Diagnostics log**, open the screen that should be blocked, and read the view ids with:

```bash
adb logcat -s 'UnscrollDiag/com.instagram.android:D'
```
