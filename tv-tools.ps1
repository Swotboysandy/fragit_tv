# fragit_tv one-click TV checks (Windows), run by privacy-check.bat and update-check.bat.
#   -Mode privacy   what the TV runs for voice, ACR (content tracking) and telemetry, with the evidence (tools\privacy.py)
#   -Mode update    after a TCL firmware update: what came back on, and the launcher still Home? (tools\check.py)
# Both read only, except "update" when you answer Y to fix: then it disables again what came back and puts
# fragit_tv back on Home, the same way the first cleanup did (tools\recovery.py keeps the undo record).
param(
    [Parameter(Mandatory = $true)][ValidateSet('privacy', 'update')][string]$Mode,
    [string]$TvHost = ''
)
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$py = Join-Path $here 'tools\venv\Scripts\python.exe'
$ipFile = Join-Path $here 'tv-ip.txt'   # remembered between runs; not committed

# Python helpers the checks run on (the same venv install.bat makes).
if (-not (Test-Path $py)) {
    $sysPy = (Get-Command python -ErrorAction SilentlyContinue).Source
    if (-not $sysPy) { throw 'Install Python 3 from python.org first, then run this again.' }
    Write-Host 'Setting up the Python helpers (once)...'
    & $sysPy -m venv (Join-Path $here 'tools\venv')
    & $py -m pip install -q -r (Join-Path $here 'tools\requirements.txt')
}

if (-not $TvHost -and (Test-Path $ipFile)) { $TvHost = (Get-Content $ipFile -Raw).Trim() }
$typed = Read-Host "TV IP address [$TvHost]"
if ($typed) { $TvHost = $typed.Trim() }
if (-not $TvHost) { throw 'No TV IP given.' }
Set-Content -Path $ipFile -Value $TvHost -Encoding ascii
$env:TV_HOST = $TvHost
Write-Host "Using $TvHost. USB debugging must be on; press Allow on the TV if it asks."

$reports = Join-Path $here 'reports'
New-Item -ItemType Directory -Force $reports | Out-Null
$stamp = Get-Date -Format 'yyyy-MM-dd_HHmm'

if ($Mode -eq 'privacy') {
    $out = Join-Path $reports "privacy-$stamp.txt"
    & $py -I (Join-Path $here 'tools\privacy.py') 2>&1 | Tee-Object -FilePath $out
    Write-Host "`nSaved to $out"
    Write-Host 'Nothing was changed on the TV. To switch something off, add its name to my-tv\debloat.txt and run'
    Write-Host '  tools\disable.ps1 -TvHost <ip> my-tv\debloat.txt'
    Start-Process notepad.exe $out
    return
}

$list = Join-Path $here 'my-tv\debloat.txt'
$out = Join-Path $reports "update-check-$stamp.txt"
& $py -I (Join-Path $here 'tools\check.py') $list 'com.fragthebox.home' 2>&1 | Tee-Object -FilePath $out
$code = $LASTEXITCODE
if ($code -eq 0) {
    Write-Host "`nAll good: nothing came back, and fragit_tv is still Home." -ForegroundColor Green
} elseif ($code -eq 1) {
    $fix = Read-Host "`nThe TV drifted (see above). Fix it now: switch those apps off again and put fragit_tv back on Home? (Y/N)"
    if ($fix -match '^[Yy]') {
        & $py -I (Join-Path $here 'tools\check.py') --fix $list 'com.fragthebox.home' 2>&1 | Tee-Object -FilePath $out -Append
        if ($LASTEXITCODE -eq 0) { Write-Host 'Fixed.' -ForegroundColor Green }
        else { Write-Warning 'Some of it could not be fixed; the lines above say what.' }
    }
} else {
    Write-Warning 'Could not read the TV (is USB debugging on and the IP right?). Nothing was changed.'
}
Write-Host "Saved to $out"
