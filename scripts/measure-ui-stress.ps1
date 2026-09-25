param(
    [string]$JavaPath        = "java",
    [string]$JavaFxClasspath,
    [string]$JavaFxModulePath,
    [string]$NativeClasspath = "jxparallel-examples-native\target\classes;jxparallel-ui\target\classes;jxparallel-core\target\classes",
    [string]$NativeDependencies,
    [int]$Runs            = 5,
    [int]$RefreshCount    = 500,
    [int]$SustainedFrames = 600,
    [string]$Output       = "docs\ui-stress-results.csv"
)

# Runs JavaFxStressRunner and NativeStressRunner alternately in fresh JVMs.
# Every "JX_METRIC key=value" line the runners print becomes a CSV column; process memory
# (working set, private bytes, handles) is sampled outside the JVM every 10 ms.

$ErrorActionPreference = "Stop"
[System.Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::InvariantCulture

function Invoke-StressCase([string]$Name, [string]$Classpath, [string]$MainClass, [int]$Run) {
    $out = Join-Path $env:TEMP "jxstress-$Name-$Run-out.txt"
    $err = Join-Path $env:TEMP "jxstress-$Name-$Run-err.txt"
    Remove-Item $out, $err -Force -ErrorAction SilentlyContinue

    $jvmArgs = [System.Collections.Generic.List[string]]::new()
    $jvmArgs.Add("-Djx.stress.refreshCount=$RefreshCount")
    $jvmArgs.Add("-Djx.sustained.frames=$SustainedFrames")
    if ($Name -eq "javafx" -and -not [string]::IsNullOrWhiteSpace($JavaFxModulePath)) {
        $jvmArgs.Add("--module-path"); $jvmArgs.Add("`"$JavaFxModulePath`"")
        $jvmArgs.Add("--add-modules"); $jvmArgs.Add("javafx.controls")
    }
    $jvmArgs.Add("-cp"); $jvmArgs.Add("`"$Classpath`""); $jvmArgs.Add($MainClass)

    $sw   = [System.Diagnostics.Stopwatch]::StartNew()
    $proc = Start-Process -FilePath $JavaPath -ArgumentList $jvmArgs.ToArray() `
                -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    $peakWs = 0L; $peakPb = 0L; $peakHandles = 0; $sumWs = 0.0; $n = 0
    while (-not $proc.HasExited) {
        try {
            $p = Get-Process -Id $proc.Id -ErrorAction Stop
            if ($p.WorkingSet64 -gt 0) {
                $peakWs = [math]::Max($peakWs, $p.WorkingSet64)
                $peakPb = [math]::Max($peakPb, $p.PrivateMemorySize64)
                $peakHandles = [math]::Max($peakHandles, $p.HandleCount)
                $sumWs += $p.WorkingSet64; $n++
            }
        } catch {}
        Start-Sleep -Milliseconds 10
    }
    $proc.WaitForExit(); $sw.Stop()

    $row = [ordered]@{
        implementation         = $Name
        run                    = $Run
        exit_code              = $proc.ExitCode
        wall_ms                = [math]::Round($sw.Elapsed.TotalMilliseconds, 1)
        peak_working_set_bytes = $peakWs
        avg_working_set_bytes  = if ($n) { [math]::Round($sumWs / $n, 0) } else { 0 }
        peak_private_bytes     = $peakPb
        peak_handles           = $peakHandles
    }
    $txt = if (Test-Path $out) { Get-Content $out -Raw } else { "" }
    foreach ($m in [regex]::Matches($txt, "(?m)^JX_METRIC\s+(\w+)=(-?[0-9]+)\s*$")) {
        $row[$m.Groups[1].Value] = [long]$m.Groups[2].Value
    }
    if ($row.Contains("first_paint_ns") -and $row.Contains("process_start_ns")) {
        $row["startup_to_first_paint_ms"] = [math]::Round(($row["first_paint_ns"] - $row["process_start_ns"]) / 1e6, 1)
    }
    if (-not $row.Contains("heap_live_after_gc_bytes")) {
        Write-Warning "$Name run $Run did not finish; stderr: $(Get-Content $err -Raw -ErrorAction SilentlyContinue)"
    }
    Remove-Item $out, $err -Force -ErrorAction SilentlyContinue
    return [pscustomobject]$row
}

if ([string]::IsNullOrWhiteSpace($JavaFxClasspath)) {
    throw "Provide -JavaFxClasspath (jxparallel-examples\target\classes) and -JavaFxModulePath (OpenJFX jars)"
}
$nativeCp = $NativeClasspath
if (-not [string]::IsNullOrWhiteSpace($NativeDependencies)) { $nativeCp += ";$NativeDependencies" }

$results = [System.Collections.Generic.List[object]]::new()
for ($r = 1; $r -le $Runs; $r++) {
    Write-Host "[$r/$Runs] JavaFX..."
    $results.Add((Invoke-StressCase "javafx" $JavaFxClasspath "com.jxparallel.examples.JavaFxStressRunner" $r))
    Write-Host "[$r/$Runs] JXParallel native (Skia + OpenGL)..."
    $results.Add((Invoke-StressCase "jxparallel-native" $nativeCp "com.jxparallel.examples.nativeui.NativeStressRunner" $r))
}

# Every run gets every column, so Export-Csv does not drop late keys.
$columns = $results | ForEach-Object { $_.PSObject.Properties.Name } | Select-Object -Unique
$results | Select-Object $columns | Export-Csv -Path $Output -NoTypeInformation -Encoding UTF8

# Median per metric per implementation.
$summary = foreach ($column in $columns | Where-Object { $_ -notin "implementation", "run" }) {
    $line = [ordered]@{ metric = $column }
    foreach ($impl in "javafx", "jxparallel-native") {
        $values = @($results | Where-Object implementation -eq $impl | ForEach-Object { $_.$column } |
                    Where-Object { $_ -ne $null } | Sort-Object)
        $line[$impl] = if ($values.Count) { $values[[int][math]::Floor(($values.Count - 1) / 2)] } else { $null }
    }
    [pscustomobject]$line
}
$summaryPath = [IO.Path]::ChangeExtension($Output, $null).TrimEnd('.') + "-median.csv"
$summary | Export-Csv -Path $summaryPath -NoTypeInformation -Encoding UTF8
$summary | Format-Table -AutoSize
Write-Host "Saved $Output and $summaryPath"
