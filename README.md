# Hyper Home TV

**A fast, minimal, privacy-first Android TV launcher — with a cat that lives at the bottom of your screen.**

No ads. No tracking. No analytics. No account. Just your apps, a live system strip, and a little companion who occasionally chases a mouse across the screen.

![Home](docs/screenshots/home.png)

## Features

- **Clean app grid** — DPAD-first navigation, smooth focus animations, long-press context menu (move, hide, uninstall, app info)
- **Live system strip** — memory, CPU clock, storage, network link speed + IP, uptime. Tap the memory chip to sweep cached background apps
- **Companion cat** — breathes, blinks, naps on the bottom edge. While it sleeps a mouse pops out of a hole, the cat wakes with a startled "!" and chases it — both wrap seamlessly through the screen edges
- **PS5-style settings** — split-pane categories with live preview, staggered entrance animations, blur-behind pickers
- **Top bar** — clock with optional seconds, date, Wi-Fi SSID, and a weather + AQI chip (opt-in)
- **Edge fades** — tiles dissolve into the floor near the companion zone instead of fighting it
- **Reorder / hide apps** — long-press a tile to move it, hide it, or uninstall it

![Mouse chase](docs/screenshots/mouse-chase.png)

## Settings

![Settings](docs/screenshots/settings.png)

| Category | What it holds |
|---|---|
| Personalize | Companion mode, mouse chase, hero strip, clock seconds, app labels |
| Status bar | Weather chip, Wi-Fi chip |
| System | Optimize memory, about |

![Picker](docs/screenshots/settings-picker.png)

## Privacy

Hyper Home TV runs **entirely on your device**. There is no telemetry, no crash reporting, no analytics SDK, no ads, and nothing to sign into.

The **only** network request it can ever make is the optional weather chip: an anonymous query to [Open-Meteo](https://open-meteo.com) (no API key, no account) plus a coarse IP-geolocation lookup to pick your city — and only when the chip is enabled in settings. Everything else is local.

### Permissions and why

| Permission | Used for |
|---|---|
| `INTERNET` | The opt-in weather chip only |
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | Wi-Fi chip and link-speed display |
| `ACCESS_FINE_LOCATION` | Android 12+ requires it just to read the connected SSID — never used for location |
| `REQUEST_DELETE_PACKAGES` | The uninstall option in the app context menu |
| `KILL_BACKGROUND_PROCESSES` | The optimize action (clears cached background apps) |

## Install on your TV

1. Download `hyper-launcher.apk` from the [latest release](../../releases/latest).
2. Enable **Unknown sources** / **Install unknown apps** on the TV.
3. Sideload it with your favorite file manager, or over ADB:

   ```sh
   adb connect <tv-ip>:5555
   adb -s <tv-ip>:5555 install hyper-launcher.apk
   adb -s <tv-ip>:5555 shell am start -n dev.hyper.launcher/.HomeActivity
   ```

4. Set it as the default launcher — either via system **Settings → Apps → Default apps → Home app**, or:

   ```sh
   adb -s <tv-ip>:5555 shell cmd package set-home-activity dev.hyper.launcher/.HomeActivity
   ```

> Works on Android TV / Google TV (Android 8.0+, minSdk 26). Tested on a TCL Google TV (Realtek).

## Build from source

```sh
cd hyper-launcher
gradle assembleDebug      # debug build → app/build/outputs/apk/debug/
```

For a signed release build, drop a `keystore.properties` in `hyper-launcher/`:

```properties
storeFile=hyper-release.keystore
storePassword=…
keyAlias=hyper
keyPassword=…
```

then `gradle assembleRelease`.

## Companion tool: `web/`

A tiny read-only ADB dashboard (single `python3` file, no deps) that visualizes every package on the TV — tagged remove / optional / critical — plus device info and live focus. Useful for debloating TCL/Realtek TVs.

```sh
python3 web/server.py <tv-ip>:5555   # serves http://localhost:8787
```

## License

[MIT](LICENSE) © Dinesh K N
