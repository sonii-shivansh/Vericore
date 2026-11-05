[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function Fail([string]$Message) {
    throw "FAIL: $Message"
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ScriptDir
$WindowsInstaller = Join-Path $ScriptDir 'install.ps1'

if (-not (Test-Path -LiteralPath $WindowsInstaller)) {
    Fail "Installer not found at $WindowsInstaller"
}

# 1. Parse and validate syntax of scripts/install.ps1
$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($WindowsInstaller, [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_.Message }
    Fail 'PowerShell syntax errors detected in scripts/install.ps1'
}

# 2. Static source contracts
$content = Get-Content -LiteralPath $WindowsInstaller -Raw
if ($content -notmatch 'api\.github\.com/repos/sonii-shivansh/Vericore/releases/latest') {
    Fail 'Windows installer missing GitHub releases endpoint'
}
if ($content -notmatch '\[string\]\$Version\s*=') {
    Fail 'Windows installer missing Version parameter'
}
if ($content -notmatch 'vericore-\$Version-\$asset\.zip') {
    Fail 'Windows installer missing archive naming pattern'
}
if ($content -notmatch 'releases/download/v\$Version') {
    Fail 'Windows installer missing release asset download URL'
}
if ($content -notmatch 'SHA256SUMS') {
    Fail 'Windows installer missing SHA256SUMS reference'
}
if ($content -notmatch 'Get-FileHash') {
    Fail 'Windows installer missing Get-FileHash'
}
if ($content -notmatch 'AMD64') {
    Fail 'Windows installer missing AMD64 architecture check'
}
if ($content -notmatch 'SetEnvironmentVariable') {
    Fail 'Windows installer missing SetEnvironmentVariable for PATH'
}
if ($content -notmatch 'filesystem root') {
    Fail 'Windows installer missing filesystem root rejection'
}
if ($content -notmatch '\.Vericore\.stage\.') {
    Fail 'Windows installer missing staging directory pattern'
}

# 3. Dynamic exercise: mock install, upgrade, and safety checks
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('vericore-installer-test-' + [guid]::NewGuid().ToString('N'))
$mockArchiveName = 'vericore-0.9.0-windows-x64.zip'
$script:mockCalls = 0

try {
    $fixtureRoot = Join-Path $testRoot 'fixture'
    $payload = Join-Path $fixtureRoot 'vericore'
    New-Item -ItemType Directory -Path (Join-Path $payload 'bin') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $payload 'lib') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $payload 'jre\bin') -Force | Out-Null

    Set-Content -LiteralPath (Join-Path $payload 'bin\vericore.bat') -Value @(
        '@echo off',
        'if "%~1"=="--version" (',
        '  echo Vericore 0.9.0',
        '  exit /b 0',
        ')',
        'echo fake cli'
    )
    Set-Content -LiteralPath (Join-Path $payload 'lib\vericore-0.9.0.jar') -Value 'fixture jar'
    Set-Content -LiteralPath (Join-Path $payload 'jre\bin\java.exe') -Value 'fixture runtime marker'

    $mockArchive = Join-Path $testRoot $mockArchiveName
    Compress-Archive -Path $payload -DestinationPath $mockArchive -Force
    $mockSums = Join-Path $testRoot 'SHA256SUMS'
    $hash = (Get-FileHash -LiteralPath $mockArchive -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath $mockSums -Value "$hash  $mockArchiveName"

    $env:VERICORE_TEST_MOCK_ARCHIVE = $mockArchive
    $env:VERICORE_TEST_MOCK_SUMS = $mockSums

    function Invoke-WebRequest {
        param([string]$Uri, [string]$OutFile, [hashtable]$Headers)
        $script:mockCalls++
        if ($Uri.EndsWith('/SHA256SUMS')) {
            Copy-Item -LiteralPath $env:VERICORE_TEST_MOCK_SUMS -Destination $OutFile -Force
        } elseif ($Uri -like '*vericore-0.9.0-windows-x64.zip') {
            Copy-Item -LiteralPath $env:VERICORE_TEST_MOCK_ARCHIVE -Destination $OutFile -Force
        } else {
            throw "Unexpected test URL: $Uri"
        }
    }

    $installRoot = Join-Path $testRoot 'safe-install\Vericore'
    $sentinel = Join-Path $testRoot 'unrelated-user-data.txt'
    Set-Content -LiteralPath $sentinel -Value 'must survive upgrade'

    # Execute installer with mock download functions
    & $WindowsInstaller -InstallRoot $installRoot -Version '0.9.0'

    if (-not (Test-Path -LiteralPath (Join-Path $installRoot 'bin\vericore.bat'))) {
        Fail 'Fresh install did not create the expected launcher.'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $installRoot 'jre\bin\java.exe'))) {
        Fail 'Fresh install omitted the bundled runtime.'
    }

    # Test upgrade preserves unrelated user data
    & $WindowsInstaller -InstallRoot $installRoot -Version '0.9.0'
    if (-not (Test-Path -LiteralPath $sentinel)) {
        Fail 'Upgrade removed unrelated user data.'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $installRoot 'lib\vericore-0.9.0.jar'))) {
        Fail 'Upgrade omitted the Vericore application jar.'
    }

    # Test unsafe user profile rejection
    $callsBeforeUnsafeTargets = $script:mockCalls
    $homeRejected = $false
    try {
        & $WindowsInstaller -InstallRoot $env:USERPROFILE -Version '0.9.0' | Out-Null
    } catch {
        $homeRejected = $true
    }
    if (-not $homeRejected) {
        Fail 'Installer accepted the user profile as its installation directory.'
    }

    # Test unrecognized destination rejection
    $unsafeRoot = Join-Path $testRoot 'not-a-vericore-installation'
    New-Item -ItemType Directory -Path $unsafeRoot -Force | Out-Null
    $unsafeSentinel = Join-Path $unsafeRoot 'keep.txt'
    Set-Content -LiteralPath $unsafeSentinel -Value 'must not be deleted'
    $unsafeRejected = $false
    try {
        & $WindowsInstaller -InstallRoot $unsafeRoot -Version '0.9.0' | Out-Null
    } catch {
        $unsafeRejected = $true
    }
    if (-not $unsafeRejected) {
        Fail 'Installer accepted a non-empty non-Vericore directory.'
    }
    if (-not (Test-Path -LiteralPath $unsafeSentinel)) {
        Fail 'Installer deleted a sentinel in a rejected destination.'
    }
    if ($script:mockCalls -ne $callsBeforeUnsafeTargets) {
        Fail 'Installer attempted to download an archive before rejecting unsafe paths.'
    }

    Write-Host 'Windows installer contract and path-safety tests passed.'
} finally {
    Remove-Item Env:VERICORE_TEST_MOCK_ARCHIVE -ErrorAction SilentlyContinue
    Remove-Item Env:VERICORE_TEST_MOCK_SUMS -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

