# AGENTS.md

Instructions for coding agents (Claude Code, Codex, Gemini CLI, Cursor, Copilot and others) working in this
repository. People start with [README.md](README.md); this file is the same project in the form an agent needs.

## What this repo is

**fragit_tv** (GitHub `Swotboysandy/fragit_tv`, local folder `frag_the_box`) is the owner's Android TV home
screen. It is **[cleanyourtv](https://github.com/Gathin23/cleanyourtv)'s launcher, copied as is**, renamed to
the package `com.fragthebox.home`, with the owner's look on top:

1. **First choice: the theme** in `themes/frag/` (cleanyourtv's own theme system: `config.json` + pictures).
2. **Only when no theme key can do it:** a small, clearly marked change in `launcher/` (the "frag additions" below).

## Rules from the owner (do not break these)

- **Never replace or rewrite the launcher**, and never add a second launcher. In October 2026 a rewritten
  "console" home was built and rejected: the owner wants cleanyourtv's own smooth home, styled. Change the look
  with theme keys first; touch launcher code only for something a key cannot do, keep the change small, and
  mark it with a `frag:` comment.
- **The TV runs exactly one launcher:** `com.fragthebox.home`. Don't leave `com.gathin.tvhome` or any other
  launcher installed beside it.
- **Focus has no white:** no white rim, white slab or white pill on focus. Focus is the tile lifting on a deep
  shadow (`glass_style: lift`) and buttons/rows brightening as glass. Don't fade unfocused tiles.
- **Disable, never uninstall** apps on the TV, and back up first (cleanyourtv's rules, below).
- **Ask before** deleting releases, rewriting git history, changing the git remote, or anything on the TV the
  owner did not ask for. Never add a `Co-Authored-By` line to commits.

## Repo map

| Path | What it is |
|---|---|
| `launcher/` | cleanyourtv's Android launcher (Java, no Kotlin), package `com.fragthebox.home`, with the frag additions |
| `themes/frag/` | **The owner's style**: `config.json`, `wallpaper.jpg` (default: Spider-Man), `wallpapers/` (all pictures), `CREDITS.md` |
| `my-tv/debloat.txt` | The apps switched off on the owner's TV (21), the list `update-check.bat` checks against |
| `tools/` | cleanyourtv's tooling: `disable`/`restore` (debloat), `theme` (push a theme), `tv.py` (ADB client), `check.py` (firmware drift), `privacy.py` (voice/ACR/telemetry audit) |
| `examples/` | cleanyourtv's tested debloat lists per TV model (the owner's TV matches `tcl-uniontv-latin-america`) |
| `install.bat` / `install.ps1` | One-click install: adb, backup, latest release APK, set Home, grant permissions, push `themes/frag` |
| `uninstall.bat` | Removes the launcher; Android falls back to the TV's own Home |
| `privacy-check.bat` / `update-check.bat` → `tv-tools.ps1` | One-click privacy audit and firmware-update check (with a Y/N fix) |
| `docs/screenshots/` | Screenshots taken on the TV, used by the README |

Not committed (see `.gitignore`): `backup/`, `reports/`, `tv-ip.txt`, `tools/venv/`, `tools/adbkey/`, `adb/`,
build outputs, `*.apk`.

## The frag additions in `launcher/` (everything else is upstream cleanyourtv)

| Feature | Files |
|---|---|
| OLED focus: `glass_style: "lift"`, no highlight, focused tile rises on a black shadow | `Config.java` (GLASS_LIFT), `GlassView.java` (draws nothing), `AppAdapter.java` (lift on focus and bind) |
| Glass without white: brighter glass pane on focus, white text | `res/drawable/pill_bg.xml`, `input_row_bg.xml`, `pill_text*.xml` |
| Apple-style dock glass: milky pane, top sheen, light-catching rim | `DockDecoration.java` |
| iOS "Clear" glass dock: wallpaper refracted at the rim with a colour fringe, **pre-rendered once per wallpaper** (Android 9 has no live shaders) | `ClearGlass.java`, hooks in `WallpaperPlayer.java` (half-size software copy) and `DockDecoration.java` |
| Brightness button (top bar): Auto by time of day, 100–20 %, TV picture settings; a dim overlay over every app | `Brightness.java`, `BrightnessService.java`, `BrightnessActivity.java`, `res/drawable/ic_brightness.xml`, `btnBrightness` in `activity_main.xml`, `MainActivity.java` |
| Weather under the date from the network's public IP (ipwho.is + Open-Meteo, 30 min cache) | `Weather.java`, `weather` TextView in `activity_main.xml`, `MainActivity.java` (onCreate + onResume) |
| Dark fades top and bottom so text and apps read on any picture | `res/drawable/top_fade.xml`, `bottom_fade.xml`, views in `activity_main.xml` |
| App name | `res/values/strings.xml` (`app_name`) |

Permissions added: `SYSTEM_ALERT_WINDOW` + `FOREGROUND_SERVICE` (brightness overlay), `INTERNET` (weather).
The installer grants the overlay with `appops set com.fragthebox.home SYSTEM_ALERT_WINDOW allow`.

## The owner's TV

- **iFFALCON 32F2A** (TCL-built, reports `UnionTV`), Android TV 9 (SDK 28), Realtek RTD284x, ~1 GB RAM, 1280×720 screenshots.
- IP `192.168.29.186`, ADB on port 5555 with USB debugging only (no "Wireless debugging" on Android 9).
- What the TV **can't** do, already checked: apps can't change the panel backlight (no `com.realtek.menuslices`
  provider), so brightness is a software dim layer; TCL's own Picture menu is `com.tcl.settings/.MainActivity`.
  Netflix/Prime/YouTube publish **no** Watch Next rows here, so a "Continue watching" row would be empty.
- The firmware turns Google Assistant (`katniss`), the offline hotword, Ambient Mode, TTS and TalkBack back on
  at every boot; they are left on and kept out of `my-tv/debloat.txt`.

## How to build, install and test (Windows)

```bash
cd launcher
export JAVA_HOME="C:/Program Files/Java/jdk-17"
./gradlew.bat assembleRelease --console=plain -q --no-daemon -Dorg.gradle.jvmargs="-Xmx1g"
adb -s 192.168.29.186:5555 install -r app/build/outputs/apk/release/app-release.apk
```

```bash
TV_HOST=192.168.29.186 tools/theme.sh themes/frag     # push the theme after changing themes/frag
```

- Use `--no-daemon -Xmx1g`: the owner's PC runs low on memory and a lingering Gradle daemon causes
  "file in use" (R8) and JVM crash logs (`hs_err_pid*.log`, ignored by git).
- In Git Bash set `MSYS_NO_PATHCONV=1` before `adb shell screencap -p /sdcard/x.png`, or the path is mangled.
- Check every visual change on the TV itself: `adb shell screencap -p /sdcard/x.png` then `adb pull`.
  Live TV, HDMI and some apps screenshot black; that is the TV, not a bug.
- Home must be `com.fragthebox.home/.MainActivity`:
  `adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN`.

## Releases

1. Bump `versionCode` (×10 per minor) and `versionName` in `launcher/app/build.gradle`, build, install on the TV.
2. Commit, push, then `gh release create vX.Y.Z <apk> -R Swotboysandy/fragit_tv --title "fragit_tv X.Y.Z"`
   with notes and the APK's SHA-256. The installer always downloads the latest release.
3. The APK is signed with the debug key (as cleanyourtv's); keep it so updates install in place.

## Cleaning a TV: cleanyourtv's safety rules, in brief

- Disable with `pm disable-user --user 0` through `tools/disable.ps1`/`.sh` (it backs up and writes an undo
  record); undo with `tools/restore.ps1`/`.sh` and the same list. Never `pm uninstall`, never root.
- Keep what the TV needs: the TV app and input services behind HDMI/tuner (`com.tcl.tv`, `com.tcl.tvinput`,
  `com.tcl.tvpassthrough`), remote pairing, the keyboard, Play Store/Services, `com.tcl.settings`.
- Never disable Android core packages (`android`, `com.android.systemui`, `com.android.settings`,
  `com.android.tv.settings`, `com.android.providers.*`, …); the tools refuse such a list.
- Never start factory/test packages. Disable in small batches and screenshot between them.
- After a firmware update run `update-check.bat` (or `tools/check.py my-tv/debloat.txt com.fragthebox.home`).

## Wallpapers

Pictures live in `themes/frag/wallpapers/`, each credited in `themes/frag/CREDITS.md` (uploader + source link).
`05-spider-venom.jpg` is Marvel fan art: fine on the owner's TV, a copyright risk in a public repo; keep that
in mind before promoting it further. Wallhaven pictures are credited to their uploader with the wallhaven link.
