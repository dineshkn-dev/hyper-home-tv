# hyper-home-tv

## Projects

- `web/` — read-only ADB visualizer dashboard. Run: `python3 web/server.py` (serves :8787). TV must be on `<tv-ip>:5555`.
- `hyper-launcher/` — Android TV launcher app (`dev.hyper.launcher`). Java, framework APIs only, no AndroidX, no INTERNET permission.

## Build & deploy hyper-launcher

```sh
cd hyper-launcher
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
gradle assembleDebug
adb -s <tv-ip>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <tv-ip>:5555 shell am start -n dev.hyper.launcher/.HomeActivity
adb -s <tv-ip>:5555 exec-out screencap -p > /tmp/shot.png   # verify visually
```

- SDK root is Homebrew's cmdline-tools dir, NOT ~/Library/Android/sdk.
- Gradle 9.8.1 (brew) + AGP 9.4.1 + platform-34 + build-tools 36.
- Hyper Launcher is the default home. `launcherx` + `tungsten.setupwraith` are disabled.
- Rollback if needed: `pm enable com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith && pm set-home-activity com.google.android.apps.tv.launcherx`

## TV cleanup state

- 42 packages disabled via `pm disable-user` (bloat/ads/telemetry + launcherx + setupwraith). None uninstalled.
- AirPlay kept (`com.tcl.airplay2`, `com.realtek.airplay2`); SMS/call-log/contacts AppOps denied.
