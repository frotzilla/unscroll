# Unscroll

Android app that blocks the endless parts of other apps (Reels, Shorts, For You feeds) and puts hard limits on scrolling. Runs entirely on the phone through an accessibility service. No accounts, no network.

## Features

**Short video blocking**
- Instagram Reels, Facebook Reels, YouTube Shorts, TikTok For You, Reddit, Snapchat Spotlight, LinkedIn, X, Instagram Lite, Facebook Lite. One master switch plus a switch per app.
- Instagram: let through reels friends send (swiping onward stays blocked), cover reels in the home feed, cover the home feed or skip to messages, close Stories, close comment sheets, cover the Explore grid while search keeps working.
- Facebook: choose where the Reels tab goes, let through reels in Messenger, send the home feed to Profile, Notifications, Friends, or Menu.
- YouTube: let through Shorts from links, cover Shorts rows.
- TikTok: let through videos sent in chats.

**Per app rules** (any installed app)
- Scroll limit: a full screen check in after 5, 10, 20 minutes, or any custom time of nonstop scrolling, with a wait before Keep scrolling unlocks and adjustable sensitivity.
- Daily allowance: minutes per day, then the app locks until midnight.
- Open with intent: pick messages, posting, or 5 minutes of scrolling before the app opens.

**Across apps**
- Focus sessions: lock every watched app for 25, 50, or 90 minutes.
- Lockout: after any check in, every limited app (plus extras you add) locks for a set time.
- Free time: scheduled windows, including overnight, when rules relax for all or chosen apps.
- Snooze with an optional typed sentence after a set number of snoozes per day, and a hideable snooze button.
- PIN lock and an uninstall guard (device admin plus backing out of uninstall, force stop, and service toggle screens).
- Web filter: built in adult site list and your own list in 10 browsers, with a landing page instead.

**Today and Insights**
- Time in watched apps today with a 7 day chart, clean days (no snoozes), and a rough distance scrolled.
- Per app screen time for a day, week, or month, plus counts of blocks, check ins, lockouts, focus sessions, and opens cancelled.

## Build

Needs JDK 17 and the Android SDK (platform 34).

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open Unscroll and follow setup. The accessibility service is listed as "Unscroll protection".

## Tuning detection

Other apps change their layouts often. Detection lives in `service/ReelsGuard.kt`. Turn on **Settings > Troubleshooting > Diagnostics log**, open the screen that should be blocked, then:

```bash
adb logcat -s 'UnscrollDiag/com.instagram.android:D'
```
