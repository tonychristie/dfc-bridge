#
# DFC Bridge Startup Script for Windows
# Run it through start-dfc-bridge.cmd, which starts it with the execution
# policy bypassed for this process only.
# Supports two modes:
#   --mode=dfc   : Full DFC mode with Documentum libraries (default)
#   --mode=nodfc : Development mode without DFC dependencies
#
# Usage:
#   start-dfc-bridge.cmd              # DFC mode (default)
#   start-dfc-bridge.cmd --mode=dfc   # Explicit DFC mode
#   start-dfc-bridge.cmd --mode=nodfc # No-DFC development mode
#   start-dfc-bridge.cmd --help       # Show help
#
# Keep this file ASCII only: Windows PowerShell 5.1 reads a file without a
# byte order mark in the system code page.
#

$ErrorActionPreference = 'Stop'

$ScriptDir = $PSScriptRoot
$RunDir = Join-Path $ScriptDir 'run'
$ExtractedDir = Join-Path $RunDir 'extracted'
$PidFile = Join-Path $RunDir 'dfc-bridge.pid'

# Defaults
$Mode = 'dfc'
$DfcHome = if ($env:DFC_HOME) { $env:DFC_HOME } else { Join-Path $HOME 'documentum\shared' }
$DfcConfig = if ($env:DFC_CONFIG) { $env:DFC_CONFIG } else { Join-Path $HOME 'documentum\config' }
$ServerPort = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { '9876' }

# Required JARs for DFC mode
$RequiredJars = @('dfc.jar', 'aspectjrt.jar')
$RequiredLog4jPattern = 'log4j'

$Command = 'start-dfc-bridge.cmd'

function Get-Jars([string]$Dir, [string]$Pattern) {
    if (-not (Test-Path -LiteralPath $Dir -PathType Container)) {
        return @()
    }
    # -Filter is not used: it also matches 8.3 short names and longer extensions.
    @(Get-ChildItem -LiteralPath $Dir -File | Where-Object { $_.Name -like $Pattern } | Sort-Object Name)
}

function Format-Size([long]$Bytes) {
    $units = @('K', 'M', 'G', 'T')
    $size = [double]$Bytes
    $unit = ''
    foreach ($u in $units) {
        if ($size -lt 1024) { break }
        $size = $size / 1024
        $unit = $u
    }
    if ($unit -eq '') {
        return "$Bytes"
    }
    if ($size -lt 10) {
        return ('{0:0.0}{1}' -f ([math]::Ceiling($size * 10) / 10), $unit)
    }
    return ('{0}{1}' -f [math]::Ceiling($size), $unit)
}

# Parse command line arguments
function Show-Help {
    Write-Output 'DFC Bridge Startup Script'
    Write-Output ''
    Write-Output "Usage: $Command [OPTIONS]"
    Write-Output ''
    Write-Output 'Options:'
    Write-Output '  --mode=dfc     Run with DFC libraries (default)'
    Write-Output '  --mode=nodfc   Run without DFC libraries (development/testing mode)'
    Write-Output '  --port=PORT    Set server port (default: 9876, or %SERVER_PORT%)'
    Write-Output '  --verify       Verify DFC setup without starting the application'
    Write-Output '  --help         Show this help message'
    Write-Output ''
    Write-Output 'Environment Variables:'
    Write-Output '  DFC_HOME       Path to DFC shared libraries (default: %USERPROFILE%\documentum\shared)'
    Write-Output '  DFC_CONFIG     Path to DFC config directory (default: %USERPROFILE%\documentum\config)'
    Write-Output '  SERVER_PORT    Server port (default: 9876)'
    Write-Output ''
    Write-Output 'Required JARs in DFC_HOME:'
    Write-Output '  - dfc.jar              Core DFC library'
    Write-Output '  - aspectjrt.jar        AspectJ runtime'
    Write-Output '  - log4j*.jar           Log4j libraries (api, core, 1.2-api)'
    Write-Output ''
    Write-Output 'Examples:'
    Write-Output "  $Command                       # Start with DFC (production)"
    Write-Output "  $Command --verify              # Check DFC setup"
    Write-Output "  $Command --mode=nodfc          # Start without DFC (development)"
    Write-Output "  $Command --mode=nodfc --port=8080  # Development mode on port 8080"
    Write-Output ''
}

# Verify DFC setup
function Test-DfcSetup {
    $errors = 0
    Write-Output 'Verifying DFC setup...'
    Write-Output ''
    Write-Output "DFC_HOME: $DfcHome"
    Write-Output "DFC_CONFIG: $DfcConfig"
    Write-Output ''

    # Check DFC_HOME exists
    if (-not (Test-Path -LiteralPath $DfcHome -PathType Container)) {
        Write-Output "[ERROR] DFC_HOME directory not found: $DfcHome"
        exit 1
    }

    # Check required JARs
    Write-Output 'Checking required JARs:'
    foreach ($jar in $RequiredJars) {
        $jarPath = Join-Path $DfcHome $jar
        if (Test-Path -LiteralPath $jarPath -PathType Leaf) {
            $size = Format-Size (Get-Item -LiteralPath $jarPath).Length
            Write-Output "  [OK] $jar ($size)"
        } else {
            Write-Output "  [MISSING] $jar"
            $errors++
        }
    }

    # Check log4j JARs
    $log4jJars = Get-Jars $DfcHome "$RequiredLog4jPattern*.jar"
    if ($log4jJars.Count -gt 0) {
        Write-Output "  [OK] log4j JARs found ($($log4jJars.Count) files):"
        foreach ($jar in $log4jJars) {
            Write-Output "       - $($jar.Name)"
        }
    } else {
        Write-Output '  [MISSING] No log4j*.jar files found'
        $errors++
    }

    Write-Output ''

    # Check DFC_CONFIG
    $dfcProperties = Join-Path $DfcConfig 'dfc.properties'
    if (-not (Test-Path -LiteralPath $DfcConfig -PathType Container)) {
        Write-Output "[ERROR] DFC_CONFIG directory not found: $DfcConfig"
        $errors++
    } elseif (-not (Test-Path -LiteralPath $dfcProperties -PathType Leaf)) {
        Write-Output "[WARNING] dfc.properties not found in $DfcConfig"
        Write-Output '  You may need to create this file with docbroker settings.'
    } else {
        Write-Output '[OK] dfc.properties found'
        # Extract docbroker info
        $line = Get-Content -LiteralPath $dfcProperties | Where-Object { $_ -match '^dfc\.docbroker\.host\[0\]' } | Select-Object -First 1
        if ($line) {
            $docbroker = ($line -split '=')[1]
            if ($docbroker) {
                Write-Output "     Docbroker: $docbroker"
            }
        }
    }

    Write-Output ''
    if ($errors -eq 0) {
        Write-Output 'DFC setup verification: PASSED'
        Write-Output "You can start the application with: $Command"
        exit 0
    } else {
        Write-Output "DFC setup verification: FAILED ($errors error(s))"
        Write-Output ''
        Write-Output 'To fix, copy the required JARs from a Documentum installation:'
        Write-Output "  copy C:\path\to\documentum\shared\dfc.jar `"$DfcHome`""
        Write-Output "  copy C:\path\to\documentum\shared\aspectjrt.jar `"$DfcHome`""
        Write-Output "  copy C:\path\to\documentum\shared\log4j*.jar `"$DfcHome`""
        exit 1
    }
}

$VerifyOnly = $false

foreach ($arg in $args) {
    $arg = [string]$arg
    if ($arg -like '--mode=*') {
        $Mode = $arg.Substring($arg.IndexOf('=') + 1)
    } elseif ($arg -like '--port=*') {
        $ServerPort = $arg.Substring($arg.IndexOf('=') + 1)
    } elseif ($arg -eq '--verify') {
        $VerifyOnly = $true
    } elseif ($arg -eq '--help' -or $arg -eq '-h') {
        Show-Help
        exit 0
    } else {
        Write-Output "Unknown option: $arg"
        Write-Output 'Use --help for usage information'
        exit 1
    }
}

# Handle verify-only mode
if ($VerifyOnly) {
    Test-DfcSetup
}

# Validate mode
if ($Mode -cne 'dfc' -and $Mode -cne 'nodfc') {
    Write-Output "Error: Invalid mode '$Mode'. Must be 'dfc' or 'nodfc'"
    exit 1
}

# Find the newest built JAR
$Jar = $null
$candidates = @(Get-Jars (Join-Path $ScriptDir 'target') 'dfc-bridge-*.jar') + @(Get-Jars $ScriptDir 'dfc-bridge-*.jar')
foreach ($candidate in $candidates) {
    if ($null -eq $Jar -or $candidate.LastWriteTimeUtc -gt $Jar.LastWriteTimeUtc) {
        $Jar = $candidate
    }
}

if ($null -eq $Jar) {
    Write-Output "Error: No dfc-bridge-*.jar found in $(Join-Path $ScriptDir 'target') or $ScriptDir"
    Write-Output 'Run: mvn package'
    exit 1
}

# Must run before any extraction: replacing the extracted classes under a
# running bridge makes it fail on its next class load.
if (Test-Path -LiteralPath $PidFile -PathType Leaf) {
    $runningPid = ([string](Get-Content -LiteralPath $PidFile -Raw)).Trim()
    if ($runningPid -match '^[0-9]+$') {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId = $runningPid"
        if ($process -and $process.CommandLine -like '*DfcBridgeApplication*') {
            Write-Output "DFC Bridge is already running (PID $runningPid). Stop it before starting again."
            exit 1
        }
    }
}

# The marker lives inside the extracted tree and is written last, so a failed
# or interrupted extraction leaves no matching marker. The time is read before
# the extraction so that a jar rewritten during it is extracted again on the
# next start.
$ExtractMarker = Join-Path $ExtractedDir '.extracted'
$JarStamp = $Jar.FullName + "`n" + $Jar.LastWriteTimeUtc.ToString('o')
$currentStamp = $null
if (Test-Path -LiteralPath $ExtractMarker -PathType Leaf) {
    $currentStamp = [System.IO.File]::ReadAllText($ExtractMarker).TrimEnd("`r", "`n")
}
if ($currentStamp -cne $JarStamp) {
    Write-Output "Extracting $($Jar.FullName)"
    if (Test-Path -LiteralPath $ExtractedDir) {
        Remove-Item -LiteralPath $ExtractedDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $ExtractedDir -Force | Out-Null
    # The full path, because a tar from Git for Windows earlier on PATH reads
    # "C:" in an archive name as a remote host.
    & (Join-Path $env:SystemRoot 'System32\tar.exe') -xf $Jar.FullName -C $ExtractedDir
    if ($LASTEXITCODE -ne 0) {
        Write-Output "Error: Extracting $($Jar.FullName) failed (tar exit code $LASTEXITCODE)"
        exit 1
    }
    [System.IO.File]::WriteAllText($ExtractMarker, $JarStamp + "`n")
}

# Build Spring Boot lib classpath (exclude log4j-to-slf4j bridge to avoid conflict with DFC)
function Get-BootClasspath {
    $entries = @()
    foreach ($jar in (Get-Jars (Join-Path $ExtractedDir 'BOOT-INF\lib') '*.jar')) {
        # Skip this conflicting bridge when in DFC mode
        if ($jar.Name -like '*log4j-to-slf4j*' -and $Mode -eq 'dfc') {
            continue
        }
        $entries += $jar.FullName
    }
    $entries -join ';'
}

# Build DFC classpath (DFC log4j jars MUST come first to avoid conflicts)
function Get-DfcClasspath {
    $entries = @()

    # Add log4j jars first
    foreach ($jar in (Get-Jars $DfcHome 'log4j*.jar')) {
        $entries += $jar.FullName
    }

    # Add remaining DFC jars (skip log4j, already added, and the Documentum
    # servlet-api.jar, an older Servlet API than the embedded Tomcat's)
    foreach ($jar in (Get-Jars $DfcHome '*.jar')) {
        if ($jar.Name -like '*log4j*' -or $jar.Name -like 'servlet-api*.jar') {
            continue
        }
        $entries += $jar.FullName
    }

    $entries -join ';'
}

# JVM options for DFC compatibility (required for Java 17+)
$JvmOpts = @(
    '--add-opens java.base/sun.security.x509=ALL-UNNAMED',
    '--add-opens java.base/sun.security.util=ALL-UNNAMED',
    '--add-opens java.base/sun.security.tools.keytool=ALL-UNNAMED',
    '--add-opens java.base/sun.security.pkcs=ALL-UNNAMED',
    '--add-opens java.base/java.lang=ALL-UNNAMED',
    '--add-opens java.base/java.util=ALL-UNNAMED',
    '--add-opens java.base/java.io=ALL-UNNAMED',
    '--add-opens java.base/java.security=ALL-UNNAMED'
)

# Build classpath based on mode
$BootCp = Get-BootClasspath
$BootClasses = Join-Path $ExtractedDir 'BOOT-INF\classes'

if ($Mode -eq 'dfc') {
    # DFC mode: DFC jars first, then config, then app classes, then boot libs
    if (-not (Test-Path -LiteralPath $DfcHome -PathType Container)) {
        Write-Output "Error: DFC_HOME directory not found at $DfcHome"
        Write-Output 'Either set DFC_HOME environment variable or use --mode=nodfc'
        exit 1
    }

    $DfcCp = Get-DfcClasspath
    $DfcConfigPath = [System.IO.Path]::GetFullPath($DfcConfig)
    $Cp = "$DfcCp;$DfcConfigPath;$BootClasses;$BootCp"

    Write-Output 'Starting DFC Bridge in DFC mode...'
    Write-Output "  DFC_HOME: $DfcHome"
    Write-Output "  Port: $ServerPort"
} else {
    # No-DFC mode: just app classes and boot libs
    $Cp = "$BootClasses;$BootCp"

    Write-Output 'Starting DFC Bridge in NO-DFC mode (development/testing)...'
    Write-Output "  Port: $ServerPort"
    Write-Output '  Note: DFC operations will return 503 Service Unavailable'
}

Write-Output ''

# Windows PowerShell 5.1 joins -ArgumentList with spaces and does not quote,
# so the classpath is quoted here to survive a path that contains a space.
$JavaArgs = ($JvmOpts -join ' ') + " -Dserver.port=$ServerPort -cp `"$Cp`" com.spirecentral.dfcbridge.DfcBridgeApplication"

# Run the application
$Bridge = Start-Process -FilePath 'java' -ArgumentList $JavaArgs -WorkingDirectory $RunDir -NoNewWindow -PassThru
# Reading the handle now keeps ExitCode available after the process exits.
$null = $Bridge.Handle
Set-Content -LiteralPath $PidFile -Value $Bridge.Id -Encoding Ascii
$Bridge.WaitForExit()
exit $Bridge.ExitCode
