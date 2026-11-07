# Vericore local release-preparation helper for Windows.
# Official packaging and publishing are performed by .github/workflows/release.yml.
# Run this helper to verify release readiness before tagging or certifying a release candidate.

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Version,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ExtraArguments
)

$ErrorActionPreference = "Stop"

if ($ExtraArguments -and $ExtraArguments.Count -gt 0) {
    throw "Unexpected additional arguments: $($ExtraArguments -join ' '). Usage: .\prepare-release.ps1 [-Version <target-version>]"
}

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$versionFile = ".\src\main\kotlin\com\vericore\core\Version.kt"
if (-not (Test-Path $versionFile)) {
    throw "Version file not found: $versionFile"
}

$versionSource = Get-Content $versionFile -Raw
$versionMatches = [regex]::Matches($versionSource, 'const val current: String = "([^"]+)"')
if ($versionMatches.Count -eq 0) {
    throw "No matching Version.current declaration found in $versionFile"
}
if ($versionMatches.Count -gt 1) {
    throw "Ambiguous application version: multiple ($($versionMatches.Count)) Version.current declarations found in $versionFile"
}
$applicationVersion = $versionMatches[0].Groups[1].Value

if ($applicationVersion -notmatch '^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$') {
    throw "Application version in Version.kt is not a valid release version format: '$applicationVersion'"
}

if ([string]::IsNullOrWhiteSpace($Version)) {
    $targetVersion = $applicationVersion
} else {
    $strictMatch = [regex]::Match($Version, '^(v)?([0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?)$')
    if (-not $strictMatch.Success) {
        throw "Invalid release version format: '$Version'. Expected [v]MAJOR.MINOR.PATCH."
    }
    $targetVersion = $strictMatch.Groups[2].Value
}

$rawProperties = & .\gradlew.bat -q properties
if ($LASTEXITCODE -ne 0) {
    throw "Gradle properties command failed with exit code $LASTEXITCODE"
}

$versionLine = ($rawProperties | Select-String '^version:' | Select-Object -First 1)
if ($null -eq $versionLine -or [string]::IsNullOrWhiteSpace($versionLine.ToString())) {
    throw "Unable to resolve Gradle project version from gradlew properties output"
}

$declared = $versionLine.ToString().Split(':', 2)[1].Trim()
if ($declared -notmatch '^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$') {
    throw "Malformed Gradle project version: '$declared'"
}

if ($declared -ne $targetVersion) {
    throw "Gradle version mismatch: declared '$declared' != target '$targetVersion'"
}
if ($applicationVersion -ne $targetVersion) {
    throw "Application version mismatch: Version.kt '$applicationVersion' != target '$targetVersion'"
}
if ($declared -ne $applicationVersion) {
    throw "Version mismatch: Gradle '$declared' != Version.kt '$applicationVersion'"
}

Write-Host "🚀 Vericore $targetVersion release preparation" -ForegroundColor Cyan

& .\gradlew.bat --no-daemon clean test installDist
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build and packaging failed with exit code $LASTEXITCODE"
}

$App = ".\build\install\vericore\bin\vericore.bat"
if (-not (Test-Path $App)) {
    throw "Expected executable not found: $App"
}

$versionOutput = & $App --version
if ($LASTEXITCODE -ne 0) {
    throw "CLI version query failed with exit code $LASTEXITCODE"
}

$versionOutputText = ($versionOutput -join "`n").Trim()
Write-Host $versionOutputText

$expectedCliOutput = "vericore version $targetVersion"
if ($versionOutputText -cne $expectedCliOutput) {
    throw "Unexpected CLI version: '$versionOutputText' does not match expected '$expectedCliOutput'"
}

& .\scripts\prepare-runtime.ps1 -DistDir .\build\install\vericore

Write-Host ""
Write-Host "Release preparation: PASS" -ForegroundColor Green
Write-Host "Official packaging/publishing: .github/workflows/release.yml"
