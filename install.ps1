# frag_the_box one-click installer (Windows). Puts the cleanyourtv launcher, in the frag style, on an Android TV / Google TV
# over Wi-Fi: no root, no cable, nothing uninstalled. Run it with install.bat, or:
#   powershell -ExecutionPolicy Bypass -File install.ps1 [-TvHost 192.168.1.50] [-Apk path\to\app.apk]
#
# What it does, in order:
#   1. gets Google's adb (platform-tools) into .\adb if adb isn't on this PC already
#   2. connects to the TV on port 5555 (accept "Allow USB debugging?" on the TV)
#   3. saves the TV's package list and Home app to .\backup\<ip>-<time>\
#   4. installs the launcher (the newest GitHub release, or -Apk) and makes it Home
#   5. pushes the frag theme (themesrag: wallpapers, glass dock, icon buttons, dock apps)
# Undo: run uninstall.bat, or `adb uninstall com.fragthebox.home`; Android falls back to the TV's own Home.
param(
    [string]$TvHost = '',
    [string]$Apk = ''
)
$ErrorActionPreference = 'Stop'
$repo = 'Swotboysandy/frag_the_box'
$pkg = 'com.fragthebox.home'
$here = $PSScriptRoot

function Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }

# 1. adb
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    $adb = Join-Path $here 'adb\platform-tools\adb.exe'
    if (-not (Test-Path $adb)) {
        Step 'Downloading Google platform-tools (adb)'
        $zip = Join-Path $env:TEMP 'platform-tools-windows.zip'
        Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip -UseBasicParsing
        Expand-Archive $zip -DestinationPath (Join-Path $here 'adb') -Force
        Remove-Item $zip
    }
}

# 2. connect
Write-Host @"
On the TV, before you continue:
  Settings > Device Preferences > About > press OK on Build 7 times
  back > Developer options > USB debugging ON (and Network debugging if you see it)
  TV and this PC on the same Wi-Fi. The TV's IP is under Settings > Network.
"@
if (-not $TvHost) { $TvHost = Read-Host 'TV IP address' }
$target = if ($TvHost -match ':') { $TvHost } else { "${TvHost}:5555" }
Step "Connecting to $target (press Allow on the TV if it asks)"
& $adb connect $target | Out-Host
$state = ''
for ($i = 0; $i -lt 30 -and $state -ne 'device'; $i++) {
    Start-Sleep 2
    $state = (& $adb -s $target get-state 2>$null) -join ''
}
if ($state -ne 'device') { throw "The TV did not accept the connection ($state). Check USB debugging and the Allow prompt, then run again." }
function Tv([string]$cmd) { (& $adb -s $target shell $cmd) -join "`n" }

$sdk = [int](Tv 'getprop ro.build.version.sdk').Trim()
$model = (Tv 'getprop ro.product.model').Trim()
Write-Host "TV: $model, Android SDK $sdk"
if ($sdk -lt 26) { throw 'This launcher needs Android 8.0 (SDK 26) or newer.' }

# 3. backup
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path $here ("backup\" + ($TvHost -replace '[^0-9A-Za-z.]', '_') + "-$stamp")
New-Item -ItemType Directory -Force $backup | Out-Null
Step "Backing up to $backup"
Tv 'pm list packages -f' | Out-File -Encoding utf8 (Join-Path $backup 'packages-all.txt')
Tv 'pm list packages -d' | Out-File -Encoding utf8 (Join-Path $backup 'packages-disabled.txt')
Tv 'getprop' | Out-File -Encoding utf8 (Join-Path $backup 'getprop.txt')
$homeBefore = (Tv 'cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN').Trim().Split("`n")[-1]
$homeBefore | Out-File -Encoding utf8 (Join-Path $backup 'home.txt')

# 4. install
if (-not $Apk) {
    Step 'Downloading the newest launcher release'
    $rel = Invoke-RestMethod "https://api.github.com/repos/$repo/releases/latest" -Headers @{ 'User-Agent' = 'frag_the_box-installer' }
    $asset = $rel.assets | Where-Object { $_.name -like '*.apk' } | Select-Object -First 1
    if (-not $asset) { throw "No APK in the latest release of $repo." }
    $Apk = Join-Path $env:TEMP $asset.name
    Invoke-WebRequest $asset.browser_download_url -OutFile $Apk -UseBasicParsing
}
Step "Installing $(Split-Path $Apk -Leaf)"
$out = (& $adb -s $target install -r $Apk) -join "`n"
Write-Host $out
if ($out -notmatch 'Success') { throw 'Install failed; the TV was left as it was.' }
Tv "pm grant $pkg android.permission.ACCESS_FINE_LOCATION" | Out-Null
# The brightness button's dim layer draws over other apps.
Tv "appops set $pkg SYSTEM_ALERT_WINDOW allow" | Out-Null
$set = Tv "cmd package set-home-activity --user 0 $pkg/.MainActivity"
$start = Tv "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $pkg/.MainActivity"
$homeNow = (Tv 'cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN').Trim().Split("`n")[-1]
if ($homeNow -notlike "$pkg/*") {
    Write-Warning "Home still resolves to '$homeNow'. Press Home on the remote and pick frag_the_box, Always."
} else {
    Write-Host "`nDone. Home is now frag_the_box (was $homeBefore)." -ForegroundColor Green
}
# 5. the frag theme: wallpapers, glass dock, icon buttons, dock apps (themes\frag). Pushed with the
# launcher's own theme tool, which needs Python 3 for its adb helper.
Step 'Putting the frag theme on the TV'
$venvPy = Join-Path $here 'tools\venv\Scripts\python.exe'
if (-not (Test-Path $venvPy)) {
    $sysPy = (Get-Command python -ErrorAction SilentlyContinue).Source
    if (-not $sysPy) {
        Write-Warning 'Python 3 is not installed, so the theme was skipped. Install Python from python.org and run install.bat again.'
        return
    }
    & $sysPy -m venv (Join-Path $here 'tools\venv')
    & $venvPy -m pip install -q -r (Join-Path $here 'tools\requirements.txt')
}
& (Join-Path $here 'tools\theme.ps1') -TvHost $TvHost (Join-Path $here 'themes\frag')
Write-Host 'Press Allow on the TV if it asks once more (the theme tool has its own key).'
Write-Host 'When you are finished, switch USB debugging off on the TV to close port 5555.'
