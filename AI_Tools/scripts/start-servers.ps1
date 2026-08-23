<#
.SYNOPSIS
    Start the L2Epic login and game servers in the correct order.

.DESCRIPTION
    Brings up "Server 2 : Sieghardt" from Deployemnt/, in the only order that works:
    MariaDB must already be listening, then the LoginServer, then the GameServer.

    The GameServer connects to the LoginServer on 127.0.0.1:9014 during its own startup and does not
    retry if nothing is there, so this script waits for 9014 to accept before launching it.

    Idempotent: anything already listening is left alone, so it is safe to run to bring up whichever
    half is missing.

    It does NOT build. The deployed jar is used as-is - see the deploy procedure in CLAUDE.md if Java
    changed.

.PARAMETER TimeoutSeconds
    How long to wait for each server to report itself up before giving up. The GameServer loads 37k
    spawns and normally registers in well under a minute.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File AI_Tools\scripts\start-servers.ps1
#>
[CmdletBinding()]
param(
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'

$Root     = 'D:\Company\L2Epic\Development\Deployemnt'
$LoginDir = Join-Path $Root 'loginserver'
$GameDir  = Join-Path $Root 'gameserver'
$GameLog  = Join-Path $GameDir 'log\general.log'
$LoginLog = Join-Path $LoginDir 'log\general.log'
$ErrorLog = Join-Path $GameDir 'log\errors.log'

# The database this server uses. There are other MariaDB installs on this machine (c:\wamp64) that
# are NOT it - see CLAUDE.md.
$MysqlStart = 'D:\company\web_dev\mysql_start.bat'

$RegisteredMarker = 'Registered on login as Server 2'

function Test-Listening
{
    param([int]$Port)

    $conn = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    return $null -ne $conn
}

function Get-ListenerPid
{
    param([int]$Port)

    $conn = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $conn) { return $null }
    return $conn.OwningProcess
}

function Wait-Listening
{
    param([int]$Port, [int]$Seconds)

    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline)
    {
        if (Test-Listening -Port $Port) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

# Has the server registered with login SINCE the given time?
#
# Deliberately timestamp-based rather than "count the marker and wait for the count to rise". log4j
# rolls general.log daily, so a boot that straddles midnight - or the first boot of a new day - starts
# from a fresh, empty file. A baseline counted before the roll is never exceeded afterwards, and the
# wait hangs until timeout while the server is in fact up. Reading the timestamp off the line itself
# is immune to that.
function Test-RegisteredSince
{
    param([string]$Path, [string]$Marker, [datetime]$Since)

    if (-not (Test-Path $Path)) { return $false }

    $lines = Get-Content $Path -Tail 300 -ErrorAction SilentlyContinue
    foreach ($line in $lines)
    {
        if ($line -notmatch [regex]::Escape($Marker)) { continue }

        # 23/08/2026 15:29:45,417  INFO LoginServerThread:267 - Registered on login as Server 2 : ...
        if ($line -match '^(\d{2})/(\d{2})/(\d{4})\s+(\d{2}):(\d{2}):(\d{2})')
        {
            $stamp = Get-Date -Year $Matches[3] -Month $Matches[2] -Day $Matches[1] `
                              -Hour $Matches[4] -Minute $Matches[5] -Second $Matches[6] -Millisecond 0
            if ($stamp -ge $Since) { return $true }
        }
    }

    return $false
}

function Wait-Registered
{
    param([string]$Path, [string]$Marker, [datetime]$Since, [int]$Seconds)

    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline)
    {
        if (Test-RegisteredSince -Path $Path -Marker $Marker -Since $Since) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

# Start-Process must point straight at the .bat with -WorkingDirectory. Going through
# 'cmd.exe /c <bat>' fails with "is not recognized" - the argument list gets re-quoted - and the
# scripts need the working directory anyway, because their classpath is the relative './lib/*'.
function Start-Server
{
    param([string]$Directory, [string]$Script)

    $path = Join-Path $Directory $Script
    if (-not (Test-Path $path)) { throw "Missing startup script: $path" }

    Start-Process -FilePath $path -WorkingDirectory $Directory
}

Write-Host ''
Write-Host '=== L2Epic server startup ===' -ForegroundColor Cyan

# --- 1. Database -----------------------------------------------------------------------------
if (-not (Test-Listening -Port 3306))
{
    Write-Host '[FAIL] MariaDB is not listening on 3306.' -ForegroundColor Red
    Write-Host "       Start it first: $MysqlStart"
    exit 1
}
Write-Host ("[ OK ] MariaDB           3306  (pid {0})" -f (Get-ListenerPid -Port 3306))

# --- 2. LoginServer --------------------------------------------------------------------------
if (Test-Listening -Port 9014)
{
    Write-Host ("[SKIP] LoginServer  2106/9014  already up (pid {0})" -f (Get-ListenerPid -Port 9014))
}
else
{
    Write-Host '[ .. ] Starting LoginServer...'
    Start-Server -Directory $LoginDir -Script 'startLoginServer.bat'

    if (-not (Wait-Listening -Port 9014 -Seconds $TimeoutSeconds))
    {
        Write-Host "[FAIL] LoginServer did not open 9014 within $TimeoutSeconds s." -ForegroundColor Red
        Write-Host "       Check $LoginLog"
        exit 1
    }
    Write-Host ("[ OK ] LoginServer  2106/9014  (pid {0})" -f (Get-ListenerPid -Port 9014))
}

# --- 3. GameServer ---------------------------------------------------------------------------
if (Test-Listening -Port 7777)
{
    Write-Host ("[SKIP] GameServer        7777  already up (pid {0})" -f (Get-ListenerPid -Port 7777))
}
else
{
    # Anchor before launching, so a registration line from an earlier boot is not mistaken for this
    # one. Backed off two seconds to allow for sub-second rounding in the log timestamp.
    $since = (Get-Date).AddSeconds(-2)

    Write-Host '[ .. ] Starting GameServer (loads ~37k spawns, give it a minute)...'
    Start-Server -Directory $GameDir -Script 'startGameServer.bat'

    if (-not (Wait-Registered -Path $GameLog -Marker $RegisteredMarker -Since $since -Seconds $TimeoutSeconds))
    {
        Write-Host "[FAIL] GameServer did not register with login within $TimeoutSeconds s." -ForegroundColor Red
        Write-Host "       Check $GameLog and $ErrorLog"
        exit 1
    }

    # Registering with login comes BEFORE binding the client port, so 7777 is briefly still closed
    # here. Without this wait the PID below reads back empty.
    if (-not (Wait-Listening -Port 7777 -Seconds 60))
    {
        Write-Host '[FAIL] GameServer registered with login but never opened 7777.' -ForegroundColor Red
        exit 1
    }
    Write-Host ("[ OK ] GameServer        7777  (pid {0})" -f (Get-ListenerPid -Port 7777))
}

# --- 4. Boot health --------------------------------------------------------------------------
Write-Host ''
Write-Host '=== boot health ===' -ForegroundColor Cyan

$tail = Get-Content $GameLog -Tail 400 -ErrorAction SilentlyContinue
foreach ($pattern in 'WalkerRoutesTable: Loaded', 'CustomSpawnTable: Loaded', $RegisteredMarker)
{
    $hit = $tail | Select-String -SimpleMatch $pattern | Select-Object -Last 1
    if ($null -ne $hit) { Write-Host ("       {0}" -f $hit.Line.Trim()) }
}

# CustomSpawnTable silently truncates on an NPC whose type has no Java class - a low count is the
# only symptom. See Known Issue 3 in CLAUDE.md.
$spawns = $tail | Select-String -SimpleMatch 'CustomSpawnTable: Loaded' | Select-Object -Last 1
if ($null -ne $spawns -and $spawns.Line -match 'Loaded (\d+) Npc Spawn')
{
    if ([int]$Matches[1] -lt 12)
    {
        Write-Host ("[WARN] Only {0} custom spawns loaded - expected 12. Spawn loading truncated." -f $Matches[1]) -ForegroundColor Yellow
    }
}

if (Test-Path $ErrorLog)
{
    $err = Get-Item $ErrorLog
    $age = (Get-Date) - $err.LastWriteTime
    if ($age.TotalMinutes -lt 5)
    {
        Write-Host ("[WARN] errors.log was written {0:N0} min ago - this boot threw. Check it." -f $age.TotalMinutes) -ForegroundColor Yellow
    }
    else
    {
        Write-Host ("       errors.log untouched since {0:HH:mm} - clean boot." -f $err.LastWriteTime)
    }
}

Write-Host ''
Write-Host 'Both servers are up.' -ForegroundColor Green
Write-Host ''
