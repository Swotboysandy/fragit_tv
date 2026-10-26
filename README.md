# frag_the_box

The [cleanyourtv](https://github.com/Gathin23/cleanyourtv) home screen for Android TV and Google TV, unchanged
underneath, in my own style. The launcher in `launcher/` is cleanyourtv's (renamed to `com.fragthebox.home`),
so it is just as smooth; the look comes from cleanyourtv's own theme system, in [`themes/frag`](themes/frag).

## The frag style

| Part | Theme setting |
|---|---|
| My wallpapers, a new one every hour | `themes/frag/wallpapers/` + `wallpaper_rotation_minutes: 60` |
| Glass dock and a soft glow in the focused app's colour | `dock_glass`, `app_glow`, `ambient_glow` |
| Round icon buttons at the top | `icon_only_buttons`, `icon_only_inputs` |
| Netflix, Prime Video, YouTube, OliverTV in front | `dock_apps` |
| Hold OK on a tile to move it or take it off Home; App library for the rest | `tile_menu`, `app_library` |
| Quiet focus, no bounce | `glass_style: rim`, `focus_overshoot: false` |

Change the look by editing `themes/frag/config.json` (every key is in cleanyourtv's
[themes README](https://github.com/Gathin23/cleanyourtv/blob/main/themes/README.md)) or the pictures in
`themes/frag/wallpapers/`, then push it again:

```powershell
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost <tv ip> themes\frag
```

## Install (Windows)

1. On the TV: **Settings → Device Preferences → About**, press OK on **Build** seven times, then
   **Developer options → USB debugging** on.
2. TV and PC on the same Wi-Fi; note the TV's IP (**Settings → Network**). Install Python 3 if it isn't on the PC.
3. Download this repository, unzip it and double-click **`install.bat`**, type the TV's IP, and press **Allow**
   on the TV.

It installs the launcher from [Releases](https://github.com/Swotboysandy/frag_the_box/releases), makes it Home
and pushes the frag theme. **Undo:** `uninstall.bat`.

## Remove the TV's ads and bloat (optional)

`tools/` and `examples/` are cleanyourtv's debloat tooling and tested TV lists:
`tools\disable.ps1 -TvHost <ip> examples\<your-tv>\debloat.txt`. Everything is disabled, never uninstalled;
`tools\restore.ps1` with the same list undoes it.

## Credits

Launcher, tools and TV lists: [cleanyourtv](https://github.com/Gathin23/cleanyourtv) by Gathin (MIT, see
`LICENSE`). Wallpaper credits: [`themes/frag/CREDITS.md`](themes/frag/CREDITS.md). Outfit font: SIL Open Font
License (`launcher/FONT-LICENSE-OFL.txt`).
