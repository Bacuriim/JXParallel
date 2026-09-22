param(
    [int]$Runs = 5,
    [string]$OutputFile = "docs/ui-metrics-java8-32bit.csv"
)

$ErrorActionPreference = "Stop"
[System.Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::InvariantCulture
[System.Threading.Thread]::CurrentThread.CurrentUICulture = [Globalization.CultureInfo]::InvariantCulture

$javaPath = "C:\Program Files (x86)\Java\jdk1.8.0_51\bin\java.exe"
$jfx = "C:\Program Files (x86)\Java\jdk1.8.0_51\jre\lib\ext\jfxrt.jar"
$cp = "target\java8-metrics\examples;target\java8-metrics\javafx;target\java8-metrics\core;" + $jfx

function Get-MetricValue([string]$OutputText, [string]$Name) {
    $match = [regex]::Match($OutputText, "(?m)^JX_METRIC\s+$([regex]::Escape($Name))=([0-9-]+)")
    if ($match.Success) {
        return [long]$match.Groups[1].Value
    }
    return $null
}

function Invoke-Run([string]$Implementation, [int]$RunNumber) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $javaPath
    $psi.Arguments = "-Djx.metrics.implementation=$Implementation -cp `"$cp`" com.jxparallel.examples.JavaFxMetricsRunner"
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true

    $started = [System.Diagnostics.Stopwatch]::StartNew()
    $p = [System.Diagnostics.Process]::Start($psi)

    $peakWs = [int64]0
    $peakPriv = [int64]0

    while (-not $p.HasExited) {
        try {
            $p.Refresh()
            if ($p.WorkingSet64 -gt $peakWs) { $peakWs = $p.WorkingSet64 }
            if ($p.PrivateMemorySize64 -gt $peakPriv) { $peakPriv = $p.PrivateMemorySize64 }
        } catch {}
        Start-Sleep -Milliseconds 10
    }

    $outText = $p.StandardOutput.ReadToEnd()
    $errText = $p.StandardError.ReadToEnd()
    $p.WaitForExit()
    $started.Stop()

    $processStart = Get-MetricValue $outText "process_start_ns"
    $firstPaint = Get-MetricValue $outText "first_paint_ns"
    $interactionStart = Get-MetricValue $outText "interaction_start_ns"
    $interactionEnd = Get-MetricValue $outText "interaction_end_ns"
    $heapDelta = Get-MetricValue $outText "heap_delta_bytes"
    $procCpuNs = Get-MetricValue $outText "process_cpu_ns"
    $threadDelta = Get-MetricValue $outText "thread_delta"

    $startupMs = if ($firstPaint -ne $null -and $processStart -ne $null) { ($firstPaint - $processStart) / 1e6 } else { 0 }
    $interactionMs = if ($interactionStart -ne $null -and $interactionEnd -ne $null) { ($interactionEnd - $interactionStart) / 1e6 } else { 0 }
    $cpuMs = if ($procCpuNs -ne $null) { $procCpuNs / 1e6 } else { 0 }

    return [pscustomobject]@{
        Implementation = $Implementation
        Run = $RunNumber
        StartupToFirstPaintMs = [math]::Round($startupMs, 2)
        InteractionMs = [math]::Round($interactionMs, 2)
        CpuMs = [math]::Round($cpuMs, 2)
        HeapDeltaKb = [math]::Round($heapDelta / 1024.0, 2)
        PeakWorkingSetMb = [math]::Round($peakWs / 1048576.0, 2)
        PeakPrivateMemoryMb = [math]::Round($peakPriv / 1048576.0, 2)
        ThreadDelta = $threadDelta
        WallTimeMs = [math]::Round($started.Elapsed.TotalMilliseconds, 2)
    }
}

Write-Host "Iniciando bateria de benchmarks em Java 8 32-bit ($Runs repeticoes)..."
$allResults = @()
for ($i = 1; $i -le $Runs; $i++) {
    Write-Host "Executando Run $i/5..."
    $trad = Invoke-Run "traditional" $i
    $jxp = Invoke-Run "jxparallel" $i
    $allResults += $trad
    $allResults += $jxp
}

$allResults | Export-Csv -Path $OutputFile -NoTypeInformation -Encoding UTF8
$allResults | Format-Table -AutoSize
Write-Host "Concluido com sucesso! Arquivo salvo em $OutputFile"
