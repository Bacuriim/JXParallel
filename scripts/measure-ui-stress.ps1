param(
    [string]$JavaPath        = "java",
    [string]$JavaFxClasspath,
    [string]$JavaFxModulePath,
    [string]$NativeClasspath = "jxparallel-examples-native\target\classes;jxparallel-ui\target\classes;jxparallel-core\target\classes",
    [string]$NativeDependencies,
    [int]$Runs         = 5,
    [int]$RefreshCount = 500,
    [string]$Output    = "docs\ui-stress-results.csv"
)

$ErrorActionPreference = "Stop"
[System.Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::InvariantCulture

function Get-Metric([string]$Text, [string]$Name) {
    $m = [regex]::Match($Text, "(?m)^JX_METRIC\s+$([regex]::Escape($Name))=([0-9-]+)\s*$")
    if ($m.Success) { return [long]$m.Groups[1].Value }
    return $null
}

function Invoke-StressCase([string]$Name, [string]$Classpath, [string]$MainClass, [int]$Run) {
    $out = Join-Path $env:TEMP "jxstress-$Name-$Run-out.txt"
    $err = Join-Path $env:TEMP "jxstress-$Name-$Run-err.txt"
    Remove-Item $out, $err -Force -ErrorAction SilentlyContinue

    $jvmArgs = [System.Collections.Generic.List[string]]::new()
    $jvmArgs.Add("-Djx.stress.refreshCount=$RefreshCount")
    if ($Name -eq "javafx" -and -not [string]::IsNullOrWhiteSpace($JavaFxModulePath)) {
        $jvmArgs.Add("--module-path"); $jvmArgs.Add($JavaFxModulePath)
        $jvmArgs.Add("--add-modules"); $jvmArgs.Add("javafx.controls,javafx.fxml")
    }
    $jvmArgs.Add("-cp"); $jvmArgs.Add($Classpath); $jvmArgs.Add($MainClass)

    $sw   = [System.Diagnostics.Stopwatch]::StartNew()
    $proc = Start-Process -FilePath $JavaPath -ArgumentList $jvmArgs.ToArray() `
                -RedirectStandardOutput $out -RedirectStandardError $err -PassThru

    $samples = [System.Collections.Generic.List[object]]::new()
    while (-not $proc.HasExited) {
        try {
            $p = Get-Process -Id $proc.Id -ErrorAction Stop
            $samples.Add([pscustomobject]@{
                WorkingSet = $p.WorkingSet64
                PrivBytes  = $p.PrivateMemorySize64
            })
        } catch {}
        Start-Sleep -Milliseconds 10
    }
    $proc.WaitForExit(); $sw.Stop()

    $txt = if (Test-Path $out) { Get-Content $out -Raw } else { "" }
    Remove-Item $out, $err -Force -ErrorAction SilentlyContinue

    $posW   = @($samples | Where-Object { $_.WorkingSet -gt 0 })
    $peakWs = if ($posW.Count) { ($posW | Measure-Object WorkingSet -Maximum).Maximum } else { 0 }
    $peakPb = if ($posW.Count) { ($posW | Measure-Object PrivBytes  -Maximum).Maximum } else { 0 }
    $avgWs  = if ($posW.Count) { ($posW | Measure-Object WorkingSet -Average).Average  } else { 0 }

    $labelNs = Get-Metric $txt "stress_label_ns"
    $btnNs   = Get-Metric $txt "stress_button_ns"
    $listNs  = Get-Metric $txt "stress_list_ns"
    $inputNs = Get-Metric $txt "stress_input_ns"
    $totalNs = Get-Metric $txt "stress_total_ns"
    $heapD   = Get-Metric $txt "heap_delta_bytes"
    $cpuNs   = Get-Metric $txt "process_cpu_ns"
    $threads = Get-Metric $txt "thread_count"
    $fpNs    = Get-Metric $txt "first_paint_ns"
    $psNs    = Get-Metric $txt "process_start_ns"

    $startupMs = if ($fpNs -ne $null -and $psNs -ne $null) {
        [math]::Round(($fpNs - $psNs) / 1e6, 3) } else { $null }
    $cpuMs = if ($cpuNs -ne $null) { [math]::Round($cpuNs / 1e6, 3) } else { $null }

    return [pscustomobject]@{
        implementation          = $Name
        run                     = $Run
        refresh_count           = $RefreshCount
        wall_ms                 = [math]::Round($sw.Elapsed.TotalMilliseconds, 3)
        startup_to_first_paint_ms = $startupMs
        stress_total_ms         = if ($totalNs -ne $null) { [math]::Round($totalNs / 1e6, 3) } else { $null }
        label_refresh_ms        = if ($labelNs -ne $null) { [math]::Round($labelNs / 1e6, 3) } else { $null }
        button_toggle_ms        = if ($btnNs   -ne $null) { [math]::Round($btnNs   / 1e6, 3) } else { $null }
        list_refresh_ms         = if ($listNs  -ne $null) { [math]::Round($listNs  / 1e6, 3) } else { $null }
        input_refresh_ms        = if ($inputNs -ne $null) { [math]::Round($inputNs / 1e6, 3) } else { $null }
        heap_delta_bytes        = $heapD
        process_cpu_ms          = $cpuMs
        peak_working_set_bytes  = $peakWs
        avg_working_set_bytes   = [math]::Round($avgWs, 0)
        peak_private_bytes      = $peakPb
        thread_count            = $threads
    }
}

if ([string]::IsNullOrWhiteSpace($JavaFxClasspath)) {
    throw "Provide -JavaFxClasspath (jxparallel-examples\target\classes + OpenJFX jars)"
}

$nativeCp = $NativeClasspath
if (-not [string]::IsNullOrWhiteSpace($NativeDependencies)) { $nativeCp += ";$NativeDependencies" }

$results = [System.Collections.Generic.List[object]]::new()
for ($r = 1; $r -le $Runs; $r++) {
    Write-Host "[$r/$Runs] JavaFX stress (no JXParallel)..."
    $results.Add((Invoke-StressCase "javafx" $JavaFxClasspath "com.jxparallel.examples.JavaFxStressRunner" $r))

    Write-Host "[$r/$Runs] JXParallel native stress (no JavaFX)..."
    $results.Add((Invoke-StressCase "jxparallel-native" $nativeCp "com.jxparallel.examples.nativeui.NativeStressRunner" $r))
}

$results | Export-Csv -Path $Output -NoTypeInformation -Encoding UTF8
$results | Format-Table -AutoSize
Write-Host "Saved to $Output"
