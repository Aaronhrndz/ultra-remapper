param(
    [string]$TargetIp = "192.168.1.173"
)

Write-Host "Scanning ADB ports on $TargetIp..." -ForegroundColor Cyan

$code = @"
using System;
using System.Net.Sockets;
using System.Threading.Tasks;
using System.Collections.Concurrent;

public class FastPortScanner {
    public static int Scan(string ip, int startPort, int endPort, int timeoutMs) {
        var found = new ConcurrentBag<int>();
        Parallel.For(startPort, endPort + 1, new ParallelOptions { MaxDegreeOfParallelism = 256 }, (port, state) => {
            if (!found.IsEmpty) { state.Stop(); return; }
            try {
                using (var client = new TcpClient()) {
                    var task = client.ConnectAsync(ip, port);
                    if (task.Wait(timeoutMs) && client.Connected) {
                        found.Add(port);
                        state.Stop();
                    }
                }
            } catch {}
        });
        int res;
        return found.TryPeek(out res) ? res : 0;
    }
}
"@

Add-Type -TypeDefinition $code -Language CSharp -IgnoreWarnings

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$port = [FastPortScanner]::Scan($TargetIp, 30000, 49999, 120)
$sw.Stop()

if ($port -gt 0) {
    Write-Host "Found open ADB port: $port in $($sw.ElapsedMilliseconds) ms!" -ForegroundColor Green
    adb connect "$TargetIp`:$port"
    return "$TargetIp`:$port"
} else {
    Write-Host "No open ADB port found on $TargetIp ($($sw.ElapsedMilliseconds) ms)" -ForegroundColor Red
    return $null
}
