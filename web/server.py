#!/usr/bin/env python3
"""Live Android TV package/settings dashboard backed by ADB."""
import json
import os
import re
import subprocess
import sys
import threading
import time
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler

ADB = "adb"
# Target TV: pass as argv[1] or set TV_ADDR, e.g. TV_ADDR=192.168.1.50:5555
DEVICE = (sys.argv[1] if len(sys.argv) > 1
          else os.environ.get("TV_ADDR", "127.0.0.1:5555"))
PORT = 8787
ROOT = os.path.dirname(os.path.abspath(__file__))


def adb(*args, timeout=30):
    try:
        r = subprocess.run(
            [ADB, "-s", DEVICE, *args],
            capture_output=True, text=True, timeout=timeout,
        )
        return (r.stdout or "") + (r.stderr or "")
    except Exception as e:
        return f"__err__:{e}"


def sh(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


# pkg -> (friendly name, class, note)
# classes: remove | optional | critical | user | core
INFO = {
    "tv.samba.ssm.ui": ("Samba TV tracker", "remove", "ACR viewing-data tracker for ad targeting"),
    "com.tcl.waterfall.overseas": ("TCL Waterfall feed", "remove", "Sponsored content / recommendation feed"),
    "com.tcl.channelplus": ("TCL Channel+", "remove", "Ad-supported TCL channel service"),
    "com.tcl.messagebox": ("TCL notifications", "remove", "Promotional notification spam"),
    "com.tcl.logkit": ("TCL LogKit", "remove", "Diagnostic logging / telemetry"),
    "com.tcl.esticker": ("Shelf label demo", "remove", "Store demo / soundbar promo"),
    "com.tcl.exhibit": ("Exhibition mode", "remove", "Showroom demo"),
    "com.tcl.ocean.instructions": ("Instructions demo", "remove", "Retail instructions app"),
    "com.tcl.factory.view": ("Factory menu", "remove", "Factory test menu"),
    "com.tcl.partnercustomizer": ("Partner customizer", "remove", "Retail partner customization"),
    "com.tcl.useragreement": ("User agreement", "remove", "License agreement screens"),
    "com.tcl.repairguide": ("Repair guide", "remove", "Repair guide"),
    "com.tcl.hotelmenu": ("Hotel menu", "remove", "Hotel mode menu"),
    "com.tcl.usercenter": ("TCL account", "remove", "TCL account center"),
    "com.tcl.guard": ("TCL Guard", "remove", "TCL Guard promo app"),
    "com.tcl.interactive": ("TCL interactive", "remove", "Interactive service"),
    "com.tcl.dashboard": ("TCL dashboard", "optional", "Smart-home dashboard panel"),
    "com.google.android.feedback": ("Google feedback", "remove", "Crash / feedback reporting"),
    "com.google.android.syncadapters.calendar": ("Calendar sync", "remove", "Calendar sync on a TV"),
    "com.android.providers.calendar": ("Calendar provider", "remove", "Calendar storage"),
    "com.android.providers.contacts": ("Contacts provider", "remove", "Contacts storage"),
    "com.android.providers.userdictionary": ("User dictionary", "remove", "Personal dictionary"),
    "com.android.printspooler": ("Print spooler", "remove", "Printing"),
    "com.android.wallpaper.livepicker": ("Live wallpaper picker", "remove", "Live wallpapers"),
    "com.android.wallpaperbackup": ("Wallpaper backup", "remove", "Wallpaper backup"),
    "com.android.managedprovisioning": ("Work profiles", "remove", "Enterprise provisioning"),
    "com.android.backupconfirm": ("Backup confirm", "remove", "Phone backup UI"),
    "com.android.localtransport": ("Backup transport", "remove", "Phone backup transport"),
    "com.android.sharedstoragebackup": ("Storage backup", "remove", "Phone backup"),
    "com.android.htmlviewer": ("HTML viewer", "remove", "Dead weight"),
    "com.android.soundpicker": ("Sound picker", "remove", "Ringtone picker"),
    "com.android.hotspot2.osulogin": ("Hotspot login", "remove", "Passpoint login"),

    "com.google.android.katniss": ("Google Assistant", "optional", "Voice search / Assistant"),
    "com.google.android.apps.tv.dreamx": ("Ambient screensaver", "optional", "Google TV ambient mode"),
    "com.google.android.videos": ("Google TV app", "optional", "Play Movies / Google TV"),
    "com.google.android.play.games": ("Play Games", "optional", "Google Play Games"),
    "com.google.android.youtube.tvmusic": ("YouTube Music", "optional", "YouTube Music for TV"),
    "com.amazon.amazonvideo.livingroom": ("Prime Video", "optional", "Preinstalled Prime Video"),
    "com.netflix.ninja": ("Netflix", "optional", "Preinstalled Netflix"),
    "com.netflix.tokenmanager": ("Netflix token manager", "optional", "Netflix auth support"),
    "com.tcl.smartalexa": ("Alexa", "optional", "Alexa integration"),
    "com.tcl.magiconnectfree": ("Magic Connect", "optional", "TCL companion app link"),
    "com.tcl.airplay2": ("AirPlay 2", "optional", "Apple AirPlay"),
    "com.realtek.airplay2": ("AirPlay platform", "optional", "AirPlay platform service"),
    "com.tcl.miracast": ("Miracast", "optional", "Screen mirroring"),
    "com.tcl.gamebar": ("Game bar", "optional", "Game mode toolbar"),
    "com.tcl.t_solo": ("Private listening", "optional", "Bluetooth headphone audio"),
    "com.tcl.hearaid": ("Hearing aid", "optional", "Hearing aid support (medium risk)"),
    "com.tcl.ui_mediaCenter": ("Media Center", "optional", "USB / local media player"),
    "com.tcl.initsetup": ("Setup wizard", "optional", "First-run setup"),
    "com.google.android.inputmethod.latin": ("Gboard", "optional", "On-screen keyboard - keep one IME"),
    "com.tcl.inputmethod.international": ("TCL keyboard", "optional", "Intl keyboard - keep one IME"),
    "com.android.dreams.basic": ("Basic screensaver", "optional", "Fallback screensaver"),
    "com.android.captiveportallogin": ("Captive portal login", "optional", "Needed for hotel Wi-Fi logins"),
    "com.google.android.tv.remote.service": ("Phone remote service", "optional", "Phone-as-remote support"),
    "com.google.android.marvin.talkback": ("TalkBack", "optional", "Accessibility screen reader"),

    "com.tcl.suspension": ("Inputs / Source menu", "critical", "NEVER disable - kills HDMI/source switching"),
    "com.tcl.ttvs": ("T-Cast + OTA updates", "critical", "System update service - never disable"),
    "com.tcl.tv": ("Live TV", "critical", "Tuner / live TV app"),
    "com.tcl.tvinput": ("TV input service", "critical", "HDMI / antenna input framework"),
    "com.tvos": ("TCL TV OS", "critical", "TCL platform core"),
    "com.tcl.systemserver": ("TCL system server", "critical", "TCL platform core"),
    "com.tcl.providers.config": ("TCL config provider", "critical", "TCL settings provider"),
    "com.tcl.globalkeyoverlay": ("Key overlay", "critical", "Remote key handling"),
    "com.tcl.autopair": ("Remote auto-pair", "critical", "Remote control pairing"),
    "com.tcl.UpdatePeripheral": ("Peripheral updater", "critical", "Remote/accessory firmware updates"),
    "com.realtek.dongledetect": ("Realtek dongle detect", "critical", "Platform service"),
    "com.realtek.power": ("Realtek power", "critical", "Power management"),
    "com.realtek.tv.overlay.framework": ("Realtek overlay", "critical", "Platform overlay"),
    "com.realtek.wifi.resources.overlay": ("Realtek Wi-Fi overlay", "critical", "Wi-Fi resources"),
    "com.google.android.apps.mediashell": ("Chromecast built-in", "critical", "Cast receiver"),
    "com.google.android.apps.tv.launcherx": ("Google TV launcher", "critical", "Current home screen"),
    "com.tcl.tv.tclhome_passive": ("TCL fallback home", "critical", "Backup home screen"),
    "com.google.android.gms": ("Play services", "critical", "Google core"),
    "com.google.android.gsf": ("Services framework", "critical", "Google core"),
    "com.android.vending": ("Play Store", "critical", "App store"),
    "com.google.android.packageinstaller": ("Package installer", "critical", "APK installs"),
    "com.google.android.webview": ("WebView", "critical", "Web rendering"),
    "com.google.android.permissioncontroller": ("Permission controller", "critical", "Runtime permissions"),
    "com.google.android.tts": ("Google TTS", "critical", "Text-to-speech"),
    "com.tcl.android.webview": ("TCL WebView", "critical", "TCL web rendering"),

    "com.adguard.android": ("AdGuard", "user", "Ad blocker"),
    "com.apple.atve.androidtv.appletv": ("Apple TV", "user", "Apple TV app"),
    "com.graymatrix.did": ("ZEE5", "user", "ZEE5 streaming"),
    "com.jio.web.androidtv": ("JioPages", "user", "Jio browser"),
    "in.startv.hotstar": ("Disney+ Hotstar", "user", "Hotstar streaming"),
}

ORDER = {"remove": 0, "optional": 1, "user": 2, "critical": 3, "core": 4}

_state = {"packages": [], "device": {}, "settings": {}, "live": {},
          "connected": False, "updated": 0, "device_addr": DEVICE}
_lock = threading.Lock()
_versions = {}


def prettify(pkg):
    tail = pkg.split(".")[-1]
    return re.sub(r"[_-]+", " ", tail).strip().title() or pkg


def parse_pkg_list(out):
    return {l.split(":", 1)[1].strip() for l in out.splitlines() if l.startswith("package:")}


def refresh_packages():
    all_p = parse_pkg_list(sh("pm list packages"))
    if not all_p:
        return False
    dis = parse_pkg_list(sh("pm list packages -d"))
    third = parse_pkg_list(sh("pm list packages -3"))
    uninst = parse_pkg_list(sh("pm list packages -u")) - all_p
    pkgs = []
    for p in sorted(all_p | uninst):
        name, cls, note = INFO.get(p, (prettify(p), "user" if p in third else "core", ""))
        pkgs.append({
            "pkg": p, "name": name, "cls": cls, "note": note,
            "third": p in third,
            "disabled": p in dis,
            "uninstalled": p in uninst,
            "version": _versions.get(p, ""),
        })
    pkgs.sort(key=lambda x: (ORDER.get(x["cls"], 9), x["name"].lower()))
    with _lock:
        _state["packages"] = pkgs
    return True


def load_versions():
    out = sh(
        "for p in $(pm list packages | cut -d: -f2); do "
        "v=$(dumpsys package $p | grep -m1 versionName | tr -d ' ' | cut -d= -f2); "
        "echo $p'|'$v; done",
        timeout=180,
    )
    for line in out.splitlines():
        if "|" in line:
            k, v = line.split("|", 1)
            _versions[k.strip()] = v.strip()


def refresh_device():
    d = {}
    d["model"] = sh("getprop ro.product.model").strip()
    d["brand"] = sh("getprop ro.product.brand").strip()
    d["android"] = sh("getprop ro.build.version.release").strip()
    d["sdk"] = sh("getprop ro.build.version.sdk").strip()
    d["build"] = sh("getprop ro.build.display.id").strip()
    home = sh("pm resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
    d["launcher"] = home.strip().splitlines()[-1].strip() if home.strip() else ""
    d["resolution"] = sh("wm size").replace("Physical size:", "").strip()
    d["density"] = sh("wm density").replace("Physical density:", "").strip()
    df = sh("df -h /data | tail -1").split()
    if len(df) >= 4:
        d["storage"] = f"{df[2]} free of {df[1]}"
    mem = sh("dumpsys meminfo | grep -m1 'Total RAM'")
    d["ram"] = mem.split(":")[-1].strip() if ":" in mem else ""
    with _lock:
        _state["device"] = d


def refresh_settings():
    s = {}
    for ns in ("system", "secure", "global"):
        out = sh(f"settings list {ns}")
        s[ns] = dict(
            l.split("=", 1) for l in out.splitlines() if "=" in l
        )
    with _lock:
        _state["settings"] = s


def refresh_live():
    """Cheap per-cycle probes: screen wakefulness + focused app."""
    w = sh("dumpsys power | grep mWakefulness=", timeout=15)
    wake = w.split("=")[-1].strip() if "=" in w else ""
    f = sh("dumpsys window | grep mCurrentFocus", timeout=15)
    m = re.search(r" ([A-Za-z0-9_.]+)/", f)
    with _lock:
        _state["live"] = {"wake": wake, "focus": m.group(1) if m else ""}


def ensure_connected():
    if "already connected" not in adb("connect", DEVICE, timeout=10) and "connected" not in adb("connect", DEVICE, timeout=10):
        pass
    devs = adb("devices", timeout=10)
    return f"{DEVICE}\tdevice" in devs


def worker():
    ensure_connected()
    threading.Thread(target=_version_job, daemon=True).start()
    i = 0
    while True:
        ok = refresh_packages()
        with _lock:
            _state["connected"] = ok
        if ok:
            refresh_live()
        if i % 8 == 0:
            refresh_device()
            refresh_settings()
        i += 1
        with _lock:
            _state["updated"] = time.time()
        time.sleep(8)


def _version_job():
    load_versions()
    refresh_packages()


def do_action(action, pkg):
    if not re.fullmatch(r"[A-Za-z0-9._]+", pkg or ""):
        return False, "bad package name"
    cmds = {
        "disable": f"pm disable-user --user 0 {pkg}",
        "enable": f"pm enable {pkg}",
        "uninstall": f"pm uninstall -k --user 0 {pkg}",
        "restore": f"cmd package install-existing {pkg}",
    }
    cmd = cmds.get(action)
    if not cmd:
        return False, "unknown action"
    out = sh(cmd).strip()
    refresh_packages()
    ok = "Success" in out or "installed for user" in out
    return ok, out or "(no output)"


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=ROOT, **kw)

    def log_message(self, *a):
        pass

    def _json(self, obj, code=200):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.startswith("/api/state"):
            with _lock:
                self._json(dict(_state))
        else:
            super().do_GET()

    def do_POST(self):
        if self.path.startswith("/api/action"):
            n = int(self.headers.get("Content-Length", 0))
            try:
                body = json.loads(self.rfile.read(n) or b"{}")
            except Exception:
                body = {}
            ok, msg = do_action(body.get("action"), body.get("package"))
            self._json({"ok": ok, "msg": msg})
        else:
            self._json({"ok": False, "msg": "not found"}, 404)


if __name__ == "__main__":
    threading.Thread(target=worker, daemon=True).start()
    print(f"Serving on http://localhost:{PORT} (device {DEVICE})")
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
