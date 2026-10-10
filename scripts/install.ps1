[CmdletBinding()]
param(
    [string]$InstallRoot = "$env:LOCALAPPDATA\Vericore",
    [string]$Version = 'latest'
)

$ErrorActionPreference = 'Stop'

function Fail([string]$Message) {
    throw "Vericore installer error: $Message"
}

function Normalize-Path([string]$Path) {
    $full = [System.IO.Path]::GetFullPath($Path)
    $root = [System.IO.Path]::GetPathRoot($full)
    if ($full.Length -gt $root.Length) {
        $full = $full.TrimEnd([char[]]@('\', '/'))
    }
    return $full
}

function Test-SameOrDescendant([string]$Candidate, [string]$ProtectedPath) {
    $candidateNormalized = Normalize-Path $Candidate
    $protectedNormalized = Normalize-Path $ProtectedPath
    return $candidateNormalized.Equals($protectedNormalized, [System.StringComparison]::OrdinalIgnoreCase) -or
        $candidateNormalized.StartsWith($protectedNormalized.TrimEnd([char[]]@('\', '/')) + '\', [System.StringComparison]::OrdinalIgnoreCase)
}

function Test-SameOrAncestor([string]$Candidate, [string]$ProtectedPath) {
    return Test-SameOrDescendant -Candidate $ProtectedPath -ProtectedPath $Candidate
}

if ([string]::IsNullOrWhiteSpace($InstallRoot)) {
    Fail 'InstallRoot must not be empty.'
}
try {
    $requestedRoot = Normalize-Path $InstallRoot
} catch {
    Fail "InstallRoot is not a valid path: $InstallRoot"
}
$pathRoot = [System.IO.Path]::GetPathRoot($requestedRoot)
if ($requestedRoot.Equals($pathRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    Fail "Refusing to install to a filesystem root: $requestedRoot"
}

$programFilesX86 = [System.Environment]::GetEnvironmentVariable('ProgramFiles(x86)')
$protectedRoots = @(
    $env:USERPROFILE,
    $env:LOCALAPPDATA,
    $env:WINDIR,
    $env:ProgramFiles,
    $programFilesX86,
    $env:ProgramData
) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique
foreach ($protected in $protectedRoots) {
    if (Test-SameOrAncestor -Candidate $requestedRoot -ProtectedPath $protected) {
        Fail "Refusing an installation destination that is a protected location or its ancestor: $requestedRoot"
    }
}
$systemRoots = @($env:WINDIR, $env:ProgramFiles, $programFilesX86, $env:ProgramData) |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique
foreach ($systemRoot in $systemRoots) {
    if (Test-SameOrDescendant -Candidate $requestedRoot -ProtectedPath $systemRoot) {
        Fail "Refusing to install inside a protected system location: $requestedRoot"
    }
}

if ($env:PROCESSOR_ARCHITECTURE -ne 'AMD64') {
    Fail "Unsupported Windows architecture: $env:PROCESSOR_ARCHITECTURE. The published Windows package currently supports x64."
}
$installParentInput = Split-Path -Parent $requestedRoot
$installLeaf = Split-Path -Leaf $requestedRoot
if ([string]::IsNullOrWhiteSpace($installLeaf) -or $installLeaf -eq '.' -or $installLeaf -eq '..') {
    Fail "InstallRoot must name a dedicated directory: $requestedRoot"
}
if (-not (Test-Path -LiteralPath $installParentInput -PathType Container)) {
    New-Item -ItemType Directory -Path $installParentInput -Force | Out-Null
}
$installParent = (Resolve-Path -LiteralPath $installParentInput).Path
$installRoot = Normalize-Path (Join-Path $installParent $installLeaf)

# Recheck the physical parent after Resolve-Path, so junctions cannot redirect
# a lexically safe path into a protected location.
foreach ($protected in $protectedRoots) {
    if (Test-SameOrAncestor -Candidate $installRoot -ProtectedPath $protected) {
        Fail "Resolved installation destination is protected or contains a protected location: $installRoot"
    }
}
foreach ($systemRoot in $systemRoots) {
    if (Test-SameOrDescendant -Candidate $installRoot -ProtectedPath $systemRoot) {
        Fail "Resolved installation destination is inside a protected system location: $installRoot"
    }
}

if (Test-Path -LiteralPath $installRoot) {
    $existingItem = Get-Item -LiteralPath $installRoot -Force
    if (-not $existingItem.PSIsContainer) {
        Fail "InstallRoot exists but is not a directory: $installRoot"
    }
    if (($existingItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        Fail "Refusing to replace an installation directory that is a symbolic link or junction: $installRoot"
    }
    $existingChildren = @(Get-ChildItem -LiteralPath $installRoot -Force)
    if ($existingChildren.Count -gt 0) {
        $allowedNames = @('bin', 'lib', 'jre')
        $unexpected = @($existingChildren | Where-Object { $_.Name -notin $allowedNames })
        $looksLikeVericore = (Test-Path -LiteralPath (Join-Path $installRoot 'bin\vericore.bat') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $installRoot 'lib') -PathType Container) -and
            (@(Get-ChildItem -LiteralPath (Join-Path $installRoot 'lib') -Filter 'vericore-*.jar' -File -ErrorAction SilentlyContinue).Count -gt 0)
        if ($unexpected.Count -gt 0 -or -not $looksLikeVericore) {
            Fail "Refusing to replace a non-empty directory that is not a recognized Vericore installation: $installRoot"
        }
    }
}

$asset = 'windows-x64'
if ($Version -eq 'latest') {
    $release = Invoke-RestMethod -Uri 'https://api.github.com/repos/sonii-shivansh/Vericore/releases/latest' -Headers @{
        Accept = 'application/vnd.github+json'
        'X-GitHub-Api-Version' = '2022-11-28'
        'User-Agent' = 'VericoreInstaller'
    }
    $Version = [string]$release.tag_name
}
$Version = $Version -replace '^v', ''
if ($Version -notmatch '^[0-9]+[.][0-9]+[.][0-9]+([-+][0-9A-Za-z.-]+)?$') {
    Fail "Unable to resolve a valid Vericore release version: $Version"
}
$archive = "vericore-$Version-$asset.zip"
$baseUrl = "https://github.com/sonii-shivansh/Vericore/releases/download/v$Version"
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("vericore-install-" + [guid]::NewGuid().ToString('N'))
$archivePath = Join-Path $tempRoot $archive
$checksumsPath = Join-Path $tempRoot 'SHA256SUMS'
$extractRoot = Join-Path $tempRoot 'extract'
$stageRoot = Join-Path $installParent ('.Vericore.stage.' + [guid]::NewGuid().ToString('N'))
$backupRoot = Join-Path $installParent ('.Vericore.backup.' + [guid]::NewGuid().ToString('N'))
$backupMoved = $false
$newInstallActivated = $false
$installComplete = $false

try {
    New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

    Write-Host "Downloading Vericore $Version for Windows x64..."
    Invoke-WebRequest -Uri "$baseUrl/$archive" -OutFile $archivePath
    Invoke-WebRequest -Uri "$baseUrl/SHA256SUMS" -OutFile $checksumsPath

    $expectedLine = Get-Content -LiteralPath $checksumsPath | Where-Object {
        $parts = $_ -split '\s+'
        $parts.Count -ge 2 -and $parts[1] -eq $archive
    } | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($expectedLine)) {
        Fail "Checksum entry for $archive was not found."
    }
    $expected = ($expectedLine -split '\s+')[0]
    $actual = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected.ToLowerInvariant()) {
        Fail 'SHA-256 verification failed.'
    }

    New-Item -ItemType Directory -Path $extractRoot -Force | Out-Null
    Expand-Archive -LiteralPath $archivePath -DestinationPath $extractRoot -Force
    $payload = Join-Path $extractRoot 'vericore'
    if (-not (Test-Path -LiteralPath (Join-Path $payload 'bin\vericore.bat') -PathType Leaf) -or
        -not (Test-Path -LiteralPath (Join-Path $payload 'lib') -PathType Container) -or
        -not (Test-Path -LiteralPath (Join-Path $payload 'jre\bin\java.exe') -PathType Leaf)) {
        Fail 'The release archive does not contain the expected Vericore launcher, libraries, and bundled Java runtime.'
    }
    $applicationJars = @(Get-ChildItem -LiteralPath (Join-Path $payload 'lib') -Filter 'vericore-*.jar' -File)
    if ($applicationJars.Count -eq 0) {
        Fail 'The release archive does not contain the Vericore application jar.'
    }

    New-Item -ItemType Directory -Path $stageRoot -Force | Out-Null
    Get-ChildItem -LiteralPath $payload -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $stageRoot -Recurse -Force
    }

    if (Test-Path -LiteralPath $installRoot) {
        Move-Item -LiteralPath $installRoot -Destination $backupRoot
        $backupMoved = $true
    }
    try {
        Move-Item -LiteralPath $stageRoot -Destination $installRoot
        $newInstallActivated = $true
    } catch {
        if ($backupMoved -and -not (Test-Path -LiteralPath $installRoot) -and (Test-Path -LiteralPath $backupRoot)) {
            Move-Item -LiteralPath $backupRoot -Destination $installRoot
            $backupMoved = $false
        }
        throw
    }

    $binDir = Join-Path $installRoot 'bin'
    $cliVersion = (& (Join-Path $binDir 'vericore.bat') --version).Trim()
    if ($LASTEXITCODE -ne 0 -or $cliVersion -notmatch [regex]::Escape($Version)) {
        Fail "Installed CLI verification failed. Reported version: $cliVersion"
    }

    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    $pathEntries = @()
    if (-not [string]::IsNullOrWhiteSpace($userPath)) {
        $pathEntries = $userPath -split ';' | Where-Object { $_ }
    }
    if ($pathEntries -notcontains $binDir) {
        $newPath = (($pathEntries + $binDir) | Select-Object -Unique) -join ';'
        [Environment]::SetEnvironmentVariable('Path', $newPath, 'User')
    }

    $installComplete = $true
    Write-Host "Installed Vericore $cliVersion to $binDir"
    Write-Host 'Open a new PowerShell window for the updated PATH to take effect.'
}
finally {
    if (-not $installComplete -and $newInstallActivated -and (Test-Path -LiteralPath $installRoot)) {
        Remove-Item -LiteralPath $installRoot -Recurse -Force
    }
    if (-not $installComplete -and $backupMoved -and (Test-Path -LiteralPath $backupRoot) -and -not (Test-Path -LiteralPath $installRoot)) {
        Move-Item -LiteralPath $backupRoot -Destination $installRoot
    }
    if ($installComplete -and $backupMoved -and (Test-Path -LiteralPath $backupRoot)) {
        Remove-Item -LiteralPath $backupRoot -Recurse -Force
    }
    if (Test-Path -LiteralPath $stageRoot) {
        Remove-Item -LiteralPath $stageRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
