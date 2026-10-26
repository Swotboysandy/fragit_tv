# frag_the_box

A console-style home screen for Android TV and Google TV: a full-screen wallpaper, the clock in the corner,
one row of cards where the selected card grows on a spring, and a bar of icons along the bottom. Installs
over Wi-Fi in one click: no root, no cable, and nothing on the TV is uninstalled.

## Install (Windows)

1. On the TV: **Settings → Device Preferences → About**, press OK on **Build** seven times, then go back to
   **Developer options** and turn on **USB debugging** (and **Network debugging** if your TV shows it).
2. Put the TV and your PC on the same Wi-Fi and note the TV's IP address (**Settings → Network**).
3. Download this repository (Code → Download ZIP), unzip it and double-click **`install.bat`**.
4. Type the TV's IP and press **Allow** when the TV asks "Allow USB debugging?".

The installer fetches Google's `adb` if your PC doesn't have it, saves a backup of the TV's app list to
`backup\`, installs the newest launcher from [Releases](https://github.com/Swotboysandy/frag_the_box/releases)
and makes it the Home screen. Switch USB debugging off afterwards to close port 5555.

**Undo:** double-click `uninstall.bat`. Android goes back to the TV's own home screen.

## What's on the home screen

| Part | What it does |
|---|---|
| Wallpaper | Every picture in this repo's [`wallpapers/`](wallpapers) folder, a new one every 6 hours. Add or remove pictures there and every TV picks them up. |
| Clock and line | A large thin clock at the top left, and one serif line above the row that changes with the time of day. |
| Top right | Round glass buttons for Wi-Fi, Bluetooth and Settings. The Wi-Fi and Bluetooth icons morph to their "off" shape when the connection drops or Bluetooth is turned off. |
| Row | Your TV apps as 16:9 banner tiles in a matte glass band; the focused tile zooms and tints the ambient glow in its colour. A Continue watching tile (progress and **Resume**) comes first when an app publishes one to Android TV. |
| Bar | Inputs (HDMI, AV, Live TV) · Music (when a music app is installed) · Notifications (where the TV has a panel) · Home · All apps · Power |

Motion runs on native Android springs (`androidx.dynamicanimation`). Focus is one white pill that glides
between items; icons morph point by point between shapes, the way Morphicons does on the web. Inputs,
All apps and Power open as a sheet that rises from the bottom while the row slides up and fades.

## Build it yourself

Needs JDK 17 and the Android SDK (platform 34).

```bash
cd launcher
./gradlew assembleRelease
```

The APK lands in `launcher/app/build/outputs/apk/release/`. Wallpaper source, rotation and the music apps
are in `launcher/app/src/main/res/values/config.xml` (`wallpaper_repo`, `wallpaper_path`,
`wallpaper_rotate_hours`, `music_apps`).

## Optional: remove the TV's ads and bloat

`tools/` and `examples/` hold the debloat tooling and the tested per-model lists from
[cleanyourtv](https://github.com/Gathin23/cleanyourtv): `tools/disable.ps1 -TvHost <ip> examples/<your-tv>/debloat.txt`
(Python 3 and `pip install -r tools/requirements.txt` in `tools\venv`). Everything is disabled, never
uninstalled, and `tools/restore.ps1` with the same list undoes it.

## Credits

Built on the launcher, tools and TV lists of [cleanyourtv](https://github.com/Gathin23/cleanyourtv) by Gathin
(MIT). The starter wallpaper is cleanyourtv's code-drawn "ruska" picture. Icons are from
[Lucide](https://lucide.dev) (ISC, `launcher/LUCIDE-LICENSE.txt`). The Outfit and Newsreader fonts are under
the SIL Open Font License (`launcher/FONT-LICENSE-OFL.txt`, `launcher/FONT-LICENSE-NEWSREADER-OFL.txt`).
