# hyper-home-tv

## Projects

- `hyper-launcher/` — Android TV launcher app (`dev.hyper.launcher`). Java, framework APIs only, no AndroidX.
- `web/` — read-only ADB visualizer dashboard. Run: `python3 web/server.py <tv-ip>:5555` or `TV_ADDR=<tv-ip>:5555 python3 web/server.py` (serves :8787).

## Build & deploy hyper-launcher

```sh
cd hyper-launcher
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
gradle assembleDebug

TV=<your-tv-ip>:5555          # e.g. TV=192.168.1.50:5555
adb connect "$TV"
adb -s "$TV" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$TV" shell am start -n dev.hyper.launcher/.HomeActivity
adb -s "$TV" exec-out screencap -p > /tmp/shot.png   # verify visually
```

- On this dev machine, SDK root is Homebrew's cmdline-tools dir, NOT ~/Library/Android/sdk.
- Gradle 9.8.1 (brew) + AGP 9.4.1 + platform-34 + build-tools 36.
- To make it the default home: `adb -s "$TV" shell pm disable-user com.google.android.apps.tv.launcherx` (or equivalent stock launcher) then `cmd package set-home-activity dev.hyper.launcher/.HomeActivity`.
- Rollback on a TCL/Google TV: `pm enable com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith && pm set-home-activity com.google.android.apps.tv.launcherx`

## TV cleanup reference

- `web/server.py` ships a curated INFO table classifying TCL/Google TV packages as remove / optional / critical — useful for debloating any TCL Realtek-based TV.
- AirPlay kept (`com.tcl.airplay2`, `com.realtek.airplay2`); SMS/call-log/contacts AppOps denied.
