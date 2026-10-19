param(
    [string]$Target = "ultra" # "ultra", "classic", or specific IP
)

$UltraMac = "22-E5-48-CD-FA-FC"
$ClassicMac = "FE-B4-B9-0B-76-E6"

function Find-WatchIp {
    param([string]$TargetMac)
    # Ping broadcast to freshen ARP
    Start-Process -FilePath "ping" -ArgumentList "192.168.1.255 -n 1 -w 500" -NoNewWindow -Wait -ErrorAction SilentlyContinue | Out-Null
    $neighbors = Get-NetNeighbor -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.LinkLayerAddress -eq $TargetMac }
    if ($neighbors) {
        return ($neighbors | Select-Object -First 1).IPAddress
    }
    # Fallback to arp -a
    $arp = arp -a | Select-String ($TargetMac.Replace("-", "-").ToLower())
    if ($arp -match "(\d+\.\d+\.\d+\.\d+)") {
        return $matches[1]
    }
    return $null
}

$ip = $null
if ($Target -eq "ultra") {
    $ip = Find-WatchIp $UltraMac
    if (-not $ip) { $ip = "192.168.1.173" }
} elseif ($Target -eq "classic") {
    $ip = Find-WatchIp $ClassicMac
    if (-not $ip) { $ip = "192.168.1.235" }
} else {
    $ip = $Target
}

Write-Host "Target IP: $ip ($Target)" -ForegroundColor Cyan

# Check if ADB already connected
$connected = adb devices | Select-String "$ip`:(\d+)"
if ($connected -match "$ip`:(\d+)") {
    $port = $matches[1]
    Write-Host "Already connected to $ip`:$port" -ForegroundColor Green
    return "$ip`:$port"
}

# Scan open ADB ports (Wear OS uses 30000-49999 for wireless debugging)
Write-Host "Scanning common Wear OS ADB ports on $ip..." -ForegroundColor Yellow

# Try mDNS first
$mdns = adb mdns services 2>$null | Select-String "$ip`:(\d+)"
if ($mdns -match "$ip`:(\d+)") {
    $p = $matches[1]
    Write-Host "Found via mDNS: $ip`:$p" -ForegroundColor Green
    adb connect "$ip`:$p"
    return "$ip`:$p"
}

# Fast parallel TCP port scan in range 32000-48000
$portsToTest = @(5555, 43475, 46321, 43253, 40085, 44525, 36811, 36563)
# Also scan in chunks if none of known ports hit
$foundPort = $null

foreach ($p in $portsToTest) {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $iar = $tcp.BeginConnect($ip, $p, $null, $null)
    $success = $iar.AsyncWaitHandle.WaitOne(200, $false)
    if ($success -and $tcp.Connected) {
        $tcp.EndConnect($iar)
        $tcp.Close()
        $foundPort = $p
        Write-Host "Found open ADB port: $p" -ForegroundColor Green
        break
    }
    $tcp.Close()
}

if (-not $foundPort) {
    Write-Host "Quick ports failed. Doing fast sweep (30000..49999)..." -ForegroundColor Yellow
    $found = [System.Collections.Concurrent.ConcurrentBag[int]]::new()
    
    # Sweep in chunks of 500 ports
    30000..49999 | ForEach-Object -Parallel {
        $ip = $using:ip
        $p = $_
        $found = $using:found
        if ($found.Count -gt 0) { return }
        $tcp = New-Object System.Net.Sockets.TcpClient
        try {
            $iar = $tcp.BeginConnect($ip, $p, $null, $null)
            if ($iar.AsyncWaitHandle.WaitOne(60, $false) -and $tcp.Connected) {
                $tcp.EndConnect($iar)
                $found.Add($p)
            }
        } catch {} finally {
            $tcp.Close()
        }
    } -ThrottleLimit 150

    if ($found.Count -gt 0) {
        $foundPort = ($found | Select-Object -First 1)
        Write-Host "Discovered port: $foundPort" -ForegroundColor Green
    }
}

if ($foundPort) {
    adb connect "$ip`:$foundPort"
    Write-Host "Connected to $ip`:$foundPort" -ForegroundColor Green
    return "$ip`:$foundPort"
} else {
    Write-Host "No open ADB port found on $ip. Ensure Wireless Debugging is ON." -ForegroundColor Red
    return $null
}
