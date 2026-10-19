param(
    [string]$Target = "all" # "ultra", "classic", "tostadora", "all"
)

# Force mDNS backend in ADB
$env:ADB_MDNS_OPENSCREEN = "1"
$adb = "C:\Users\aaron\.local\bin\adb.exe"

Write-Host "Discovering Android devices via mDNS broadcast..." -ForegroundColor Cyan

# Query discovered services from adb mdns
$services = & $adb mdns services 2>$null

# Known device signatures
# Ultra: 192.168.1.173
# Classic: 192.168.1.235
# Tostadora (S24 Ultra): 192.168.1.222
# Big Fondue (Tablet): 192.168.1.187

$results = @()
foreach ($line in $services) {
    if ($line -match "(\S+)\s+_adb-tls-connect\._tcp\s+(\d+\.\d+\.\d+\.\d+):(\d+)") {
        $devName = $matches[1]
        $ip = $matches[2]
        $port = $matches[3]
        $friendly = switch ($ip) {
            "192.168.1.173" { "Galaxy Watch Ultra" }
            "192.168.1.235" { "Galaxy Watch 6 Classic" }
            "192.168.1.222" { "Tostadora (S24 Ultra)" }
            "192.168.1.187" { "Big Fondue (Tablet)" }
            default { "Android Device ($ip)" }
        }
        $results += [PSCustomObject]@{
            Device = $friendly
            IP     = $ip
            Port   = $port
            Id     = $devName
        }
    }
}

if ($results.Count -eq 0) {
    Write-Host "No active mDNS ADB services detected. Ensure Wireless Debugging screen is open on device." -ForegroundColor Yellow
} else {
    Write-Host "`nDiscovered Android Devices via mDNS:" -ForegroundColor Green
    $results | Format-Table -AutoSize
    
    foreach ($r in $results) {
        if ($Target -eq "all" -or 
            ($Target -eq "ultra" -and $r.IP -eq "192.168.1.173") -or
            ($Target -eq "classic" -and $r.IP -eq "192.168.1.235") -or
            ($Target -eq "tostadora" -and $r.IP -eq "192.168.1.222")) {
            Write-Host "Connecting to $($r.Device) at $($r.IP):$($r.Port)..." -ForegroundColor Cyan
            & $adb connect "$($r.IP):$($r.Port)"
        }
    }
}
