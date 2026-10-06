[CmdletBinding()]
param(
    [string]$InstallRoot = "$env:LOCALAPPDATA\Vericore"
)

$ErrorActionPreference = 'Stop'

function Fail([string]$Message) {
    throw "Vericore installer error: $Message"
}

$arch = $env:PROCESSOR_ARCHITECTURE
if ($arch -ne 'AMD64') {
    Fail "Unsupported Windows architecture: $arch. The published Windows package currently supports x64."
}

$asset = 'windows-x64'
$archive = "vericore-$asset.zip"
$baseUrl = 'https://github.com/sonii-shivansh/Vericore/releases/latest/download'
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("vericore-install-" + [guid]::NewGuid().ToString('N'))
$archivePath = Join-Path $tempRoot $archive
$checksumsPath = Join-Path $tempRoot 'SHA256SUMS'
$extractRoot = Join-Path $tempRoot 'extract'

try {
    New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

    Write-Host "Downloading latest Vericore release for Windows x64..."
    Invoke-WebRequest -Uri "$baseUrl/$archive" -OutFile $archivePath
    Invoke-WebRequest -Uri "$baseUrl/SHA256SUMS" -OutFile $checksumsPath

    $expected = ((Get-Content $checksumsPath | Where-Object { $_ -match [regex]::Escape($archive) } | Select-Object -First 1) -split '\s+')[0]
    if ([string]::IsNullOrWhiteSpace($expected)) {
        Fail "Checksum entry for $archive was not found."
    }

    $actual = (Get-FileHash -Path $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected.ToLowerInvariant()) {
        Fail "SHA-256 verification failed."
    }

    if (Test-Path $extractRoot) { Remove-Item -Recurse -Force $extractRoot }
    Expand-Archive -Path $archivePath -DestinationPath $extractRoot -Force

    $payload = Join-Path $extractRoot 'vericore'
    if (-not (Test-Path (Join-Path $payload 'bin\vericore.bat'))) {
        Fail "The release archive did not contain the expected Vericore launcher."
    }

    if (Test-Path $InstallRoot) { Remove-Item -Recurse -Force $InstallRoot }
    New-Item -ItemType Directory -Path $InstallRoot -Force | Out-Null
    Copy-Item -Path (Join-Path $payload '*') -Destination $InstallRoot -Recurse -Force

    $binDir = Join-Path $InstallRoot 'bin'
    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    $pathEntries = @()
    if (-not [string]::IsNullOrWhiteSpace($userPath)) {
        $pathEntries = $userPath -split ';' | Where-Object { $_ }
    }
    if ($pathEntries -notcontains $binDir) {
        $newPath = (($pathEntries + $binDir) | Select-Object -Unique) -join ';'
        [Environment]::SetEnvironmentVariable('Path', $newPath, 'User')
    }

    $version = (& (Join-Path $binDir 'vericore.bat') --version).Trim()
    Write-Host "Installed Vericore $version to $binDir"
    Write-Host "Open a new PowerShell window for the updated PATH to take effect."
}
finally {
    if (Test-Path $tempRoot) {
        Remove-Item -Recurse -Force $tempRoot -ErrorAction SilentlyContinue
    }
}
