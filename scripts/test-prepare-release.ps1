# Vericore regression test suite for scripts/prepare-release.ps1
# Tests release-preparation logic using isolated mock environments without executing real builds.

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ScriptDir
$TargetScript = Join-Path $RepoRoot "scripts\prepare-release.ps1"

if (-not (Test-Path $TargetScript)) {
    throw "Target script not found: $TargetScript"
}

$totalTests = 0
$passedTests = 0
$failedTests = 0

function Report-Pass([string]$id, [string]$name) {
    Write-Host "  [PASS] $($id): $name" -ForegroundColor Green
    $script:passedTests = $script:passedTests + 1
}

function Report-Fail([string]$id, [string]$message) {
    Write-Host "  [FAIL] $($id): $message" -ForegroundColor Red
    $script:failedTests = $script:failedTests + 1
}

function Run-Scenario {
    param(
        [Parameter(Mandatory = $true)][string]$Id,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$ExpectedExit,
        [Parameter(Mandatory = $true)][bool]$ExpectBuildInvoked,
        [string]$ExpectedPattern = "",
        [string]$AppVersion = "2.0.0",
        [string]$AppVersionRaw = "",
        [string]$GradleVersion = "2.0.0",
        [string]$CliOutput = "vericore version 2.0.0",
        [string]$GradleFail = "false",
        [string]$BuildFail = "false",
        [string]$OmitCliBinary = "false",
        [string]$CliFail = "false",
        [string]$PackagingFail = "false",
        [string[]]$ScriptArgs = @()
    )

    $script:totalTests = $script:totalTests + 1
    Write-Host "Running $($Id): $Name..."

    $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("vc-test-" + [System.Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

    try {
        $scriptsDir = Join-Path $tempDir "scripts"
        $srcDir = Join-Path $tempDir "src\main\kotlin\com\vericore\core"
        New-Item -ItemType Directory -Path $scriptsDir -Force | Out-Null
        New-Item -ItemType Directory -Path $srcDir -Force | Out-Null

        Copy-Item -Path $TargetScript -Destination (Join-Path $scriptsDir "prepare-release.ps1")

        # Mock Version.kt
        if (-not [string]::IsNullOrWhiteSpace($AppVersionRaw)) {
            Set-Content -Path (Join-Path $srcDir "Version.kt") -Value $AppVersionRaw -Encoding UTF8
        } else {
            $versionContent = @"
package com.vericore.core

object Version {
    const val current: String = "$AppVersion"
}
"@
            Set-Content -Path (Join-Path $srcDir "Version.kt") -Value $versionContent -Encoding UTF8
        }

        # Mock prepare-runtime.ps1
        $packagingContent = @'
param([string]$DistDir)
if ($env:MOCK_PACKAGING_FAIL -eq "true") {
    throw "Mock runtime packaging error"
}
'@
        Set-Content -Path (Join-Path $scriptsDir "prepare-runtime.ps1") -Value $packagingContent -Encoding UTF8

        # Template for CLI executable
        $binTemplateDir = Join-Path $tempDir "bin_template"
        New-Item -ItemType Directory -Path $binTemplateDir -Force | Out-Null
        $cliBatch = @'
@echo off
if "%1"=="--version" (
    if "%MOCK_CLI_FAIL%"=="true" (
        echo Mock CLI binary crash >&2
        exit 1
    )
    if defined MOCK_CLI_OUTPUT (
        echo %MOCK_CLI_OUTPUT%
    ) else (
        echo vericore version 2.0.0
    )
    exit 0
)
echo Unexpected CLI invocation: %* >&2
exit 1
'@
        Set-Content -Path (Join-Path $binTemplateDir "vericore.bat") -Value $cliBatch -Encoding Ascii

        # Mock gradlew.bat
        $gradlewBatch = @'
@echo off
if "%1"=="-q" if "%2"=="properties" (
    if "%MOCK_GRADLE_FAIL%"=="true" (
        echo Mock gradle properties error >&2
        exit 1
    )
    echo name: vericore
    if defined MOCK_GRADLE_VERSION (
        echo version: %MOCK_GRADLE_VERSION%
    ) else (
        echo version: 2.0.0
    )
    echo group: com.vericore
    exit 0
)

if "%1"=="--no-daemon" if "%2"=="clean" (
    type nul > "%~dp0.build_invoked"
    if "%MOCK_BUILD_FAIL%"=="true" (
        echo Mock build execution error >&2
        exit 1
    )
    if not "%MOCK_OMIT_CLI_BINARY%"=="true" (
        if not exist "%~dp0build\install\vericore\bin" mkdir "%~dp0build\install\vericore\bin"
        copy /y "%~dp0bin_template\vericore.bat" "%~dp0build\install\vericore\bin\vericore.bat" >nul
    )
    exit 0
)

echo Unexpected mock gradlew invocation: %* >&2
exit 1
'@
        Set-Content -Path (Join-Path $tempDir "gradlew.bat") -Value $gradlewBatch -Encoding Ascii

        # Run via isolated powershell process
        $scriptPath = Join-Path $scriptsDir "prepare-release.ps1"

        $escapedArgs = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$scriptPath`"")
        foreach ($arg in $ScriptArgs) {
            $escapedArgs += "`"$arg`""
        }

        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = "powershell.exe"
        $psi.Arguments = ($escapedArgs -join " ")
        $psi.WorkingDirectory = $tempDir
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true

        $psi.EnvironmentVariables["MOCK_GRADLE_VERSION"] = $GradleVersion
        $psi.EnvironmentVariables["MOCK_GRADLE_FAIL"] = $GradleFail
        $psi.EnvironmentVariables["MOCK_BUILD_FAIL"] = $BuildFail
        $psi.EnvironmentVariables["MOCK_OMIT_CLI_BINARY"] = $OmitCliBinary
        $psi.EnvironmentVariables["MOCK_CLI_FAIL"] = $CliFail
        $psi.EnvironmentVariables["MOCK_CLI_OUTPUT"] = $CliOutput
        $psi.EnvironmentVariables["MOCK_PACKAGING_FAIL"] = $PackagingFail

        $proc = [System.Diagnostics.Process]::Start($psi)
        $stdout = $proc.StandardOutput.ReadToEnd()
        $stderr = $proc.StandardError.ReadToEnd()
        $proc.WaitForExit()
        $actualExit = $proc.ExitCode

        $combined = ($stdout + "`n" + $stderr).Trim()
        $failedCase = $false

        if ($actualExit -ne $ExpectedExit) {
            Report-Fail $Id "Expected exit code $ExpectedExit, got $actualExit. Output: $combined"
            $failedCase = $true
        }

        $sentinelPath = Join-Path $tempDir ".build_invoked"
        $sentinelExists = Test-Path $sentinelPath

        if ($ExpectBuildInvoked -and -not $sentinelExists) {
            Report-Fail $Id "Expected .build_invoked sentinel to exist, but build was not executed"
            $failedCase = $true
        } elseif (-not $ExpectBuildInvoked -and $sentinelExists) {
            Report-Fail $Id "Expected .build_invoked sentinel NOT to exist, but build was executed"
            $failedCase = $true
        }

        if (-not [string]::IsNullOrWhiteSpace($ExpectedPattern)) {
            if ($combined -notmatch $ExpectedPattern) {
                Report-Fail $Id "Output did not match expected pattern '$ExpectedPattern'. Output: $combined"
                $failedCase = $true
            }
        }

        if (-not $failedCase) {
            Report-Pass $Id $Name
        }
    }
    finally {
        Remove-Item -Recurse -Force -LiteralPath $tempDir -ErrorAction SilentlyContinue
    }
}

Write-Host "=== Running Vericore prepare-release.ps1 Regression Test Suite ==="

# TC-01: Default matching version
Run-Scenario -Id "TC-01" -Name "Default no-argument matching version" -ExpectedExit 0 -ExpectBuildInvoked $true -ExpectedPattern "Release preparation: PASS"

# TC-02: Explicit matching version
Run-Scenario -Id "TC-02" -Name "Explicit matching target version" -ExpectedExit 0 -ExpectBuildInvoked $true -ExpectedPattern "Release preparation: PASS" -ScriptArgs @("2.0.0")

# TC-03: Single 'v' prefix
Run-Scenario -Id "TC-03" -Name "Single 'v' prefix normalized" -ExpectedExit 0 -ExpectBuildInvoked $true -ExpectedPattern "Release preparation: PASS" -ScriptArgs @("v2.0.0")

# TC-04: Repeated prefix rejected
Run-Scenario -Id "TC-04" -Name "Repeated 'vv' prefix rejected" -ExpectedExit 1 -ExpectBuildInvoked $false -ExpectedPattern "Invalid release version format" -ScriptArgs @("vv2.0.0")

# TC-05: Malformed version rejected
Run-Scenario -Id "TC-05" -Name "Malformed version rejected" -ExpectedExit 1 -ExpectBuildInvoked $false -ExpectedPattern "Invalid release version format" -ScriptArgs @("2.0")

# TC-06: Mismatched target rejected
Run-Scenario -Id "TC-06" -Name "Mismatched target rejected before build" -ExpectedExit 1 -ExpectBuildInvoked $false -ExpectedPattern "Gradle version mismatch" -ScriptArgs @("9.9.9")

# TC-07: Gradle/application version disagreement
Run-Scenario -Id "TC-07" -Name "Gradle/application version disagreement" -ExpectedExit 1 -ExpectBuildInvoked $false -ExpectedPattern "version mismatch" -GradleVersion "2.0.1"

# TC-08: Gradle properties command failure
Run-Scenario -Id "TC-08" -Name "Gradle properties failure aborts" -ExpectedExit 1 -ExpectBuildInvoked $false -ExpectedPattern "Gradle properties command failed" -GradleFail "true"

# TC-09: Build command failure
Run-Scenario -Id "TC-09" -Name "Build failure halts execution" -ExpectedExit 1 -ExpectBuildInvoked $true -ExpectedPattern "Gradle build and packaging failed" -BuildFail "true"

# TC-10: Missing CLI executable
Run-Scenario -Id "TC-10" -Name "Missing CLI executable fails closed" -ExpectedExit 1 -ExpectBuildInvoked $true -ExpectedPattern "Expected executable not found" -OmitCliBinary "true"

# TC-11: Incorrect CLI version output
Run-Scenario -Id "TC-11" -Name "Incorrect CLI version output rejected" -ExpectedExit 1 -ExpectBuildInvoked $true -ExpectedPattern "Unexpected CLI version" -CliOutput "vericore version 1.9.9"

# TC-12: Runtime packaging check failure
Run-Scenario -Id "TC-12" -Name "Runtime packaging check failure halts execution" -ExpectedExit 1 -ExpectBuildInvoked $true -ExpectedPattern "Mock runtime packaging error" -PackagingFail "true"

# TC-13: Extracted default version is malformed
Run-Scenario -Id "TC-13" -Name "Malformed default application version rejected" -ExpectedExit 1 -ExpectBuildInvoked $false -ExpectedPattern "not a valid release version format" -AppVersion "corrupt-version"

# TC-14: Unexpected additional arguments
Run-Scenario -Id "TC-14" -Name "Unexpected additional arguments rejected" -ExpectedExit 1 -ExpectBuildInvoked $false -ExpectedPattern "Unexpected additional arguments" -ScriptArgs @("2.0.0", "extra_arg")

# TC-15: Regex wildcard bypass attempt (2x0y0 must not match 2.0.0)
Run-Scenario -Id "TC-15" -Name "Regex wildcard false acceptance '2x0y0' rejected" -ExpectedExit 1 -ExpectBuildInvoked $true -ExpectedPattern "Unexpected CLI version" -CliOutput "vericore version 2x0y0"

# TC-16: Correct version embedded in extra text rejected
Run-Scenario -Id "TC-16" -Name "Correct version embedded in extra text rejected" -ExpectedExit 1 -ExpectBuildInvoked $true -ExpectedPattern "Unexpected CLI version" -CliOutput "extra text vericore version 2.0.0"

# TC-17: Multiple matching Version.current declarations rejected
$rawMulti = @"
package com.vericore.core
object Version {
    const val current: String = "2.0.0"
    const val current: String = "2.0.1"
}
"@
Run-Scenario -Id "TC-17" -Name "Multiple Version.current declarations rejected" -ExpectedExit 1 -ExpectBuildInvoked $false -ExpectedPattern "multiple \(2\) Version\.current declarations" -AppVersionRaw $rawMulti

# TC-18: Zero matching Version.current declarations rejected
$rawZero = @"
package com.vericore.core
object Version {
    const val legacy: String = "2.0.0"
}
"@
Run-Scenario -Id "TC-18" -Name "Zero Version.current declarations rejected" -ExpectedExit 1 -ExpectBuildInvoked $false -ExpectedPattern "No matching Version\.current declaration" -AppVersionRaw $rawZero

# TC-19: Incorrect case CLI version output rejected
Run-Scenario -Id "TC-19" -Name "Incorrect case CLI version output rejected" -ExpectedExit 1 -ExpectBuildInvoked $true -ExpectedPattern "Unexpected CLI version" -CliOutput "VERICORE VERSION 2.0.0"

Write-Host ""
Write-Host "=== Test Summary ==="
Write-Host "Total:  $totalTests"
Write-Host "Passed: $passedTests" -ForegroundColor Green
Write-Host "Failed: $failedTests" -ForegroundColor $(if ($failedTests -eq 0) { "Green" } else { "Red" })

if ($failedTests -ne 0) {
    exit 1
}

Write-Host "All prepare-release.ps1 regression tests passed successfully." -ForegroundColor Green
