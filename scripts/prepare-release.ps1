# Vericore local release-preparation helper for Windows.
# Official 0.8.2 packaging and publishing are performed by .github/workflows/release.yml.

$ErrorActionPreference = "Stop"
$ExpectedVersion = "0.8.2"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$declared = (& .\gradlew.bat -q properties | Select-String '^version:' | Select-Object -First 1).ToString().Split(':', 2)[1].Trim()
$versionSource = Get-Content .\src\main\kotlin\com\vericore\core\Version.kt -Raw
$match = [regex]::Match($versionSource, 'const val current: String = "([^"]+)"')
if (-not $match.Success) { throw "Unable to resolve application version from Version.kt" }
$applicationVersion = $match.Groups[1].Value

if ($declared -ne $ExpectedVersion) { throw "Gradle version mismatch: $declared" }
if ($applicationVersion -ne $ExpectedVersion) { throw "Application version mismatch: $applicationVersion" }

Write-Host "🚀 Vericore $ExpectedVersion release preparation" -ForegroundColor Cyan
& .\gradlew.bat --no-daemon clean test installDist

$App = ".\build\install\vericore\bin\vericore.bat"
if (-not (Test-Path $App)) { throw "Expected executable not found: $App" }
$versionOutput = (& $App --version).Trim()
Write-Host $versionOutput
if ($versionOutput -notmatch [regex]::Escape($ExpectedVersion)) { throw "Unexpected CLI version: $versionOutput" }

& .\scripts\prepare-runtime.ps1 -DistDir .\build\install\vericore

Write-Host ""
Write-Host "Release preparation: PASS" -ForegroundColor Green
Write-Host "Official packaging/publishing: .github/workflows/release.yml"
