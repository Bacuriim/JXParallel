param(
    [string]$JavaPath = "java",
    [string]$Classpath = "jxparallel-examples\target\classes;jxparallel-fxml\target\classes;jxparallel-javafx\target\classes;jxparallel-core\target\classes",
    [Parameter(Mandatory = $true)][string]$JavaFxModulePath,
    [int]$Runs    = 5,
    [int]$Screens = 20,
    [int]$Passes  = 3,
    [string]$Output = "docs\fxml-load-results.csv"
)

# Runs FxmlLoadBenchmark in "javafx" and "jxparallel" modes alternately, each in a fresh JVM.
# Every "JX_METRIC key=value" line becomes a CSV column; process memory is sampled every 10 ms.
# ponytail: process sampling duplicated from measure-ui-stress.ps1; extract a module if a third script needs it.

$ErrorActionPreference = "Stop"
[System.Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::InvariantCulture

function Invoke-Case([string]$Mode, [int]$Run) {
    $out = Join-Path $env:TEMP "jxfxml-$Mode-$Run-out.txt"
    $err = Join-Path $env:TEMP "jxfxml-$Mode-$Run-err.txt"
    $jvmArgs = @("-Djx.fxml.screens=$Screens", "-Djx.fxml.passes=$Passes",
                 "--module-path", "`"$JavaFxModulePath`"", "--add-modules", "javafx.controls,javafx.fxml",
                 "-cp", "`"$Classpath`"", "com.jxparallel.examples.FxmlLoadBenchmark", $Mode)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $proc = Start-Process -FilePath $JavaPath -ArgumentList $jvmArgs -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    $peakWs = 0L; $peakPb = 0L
    while (-not $proc.HasExited) {
        try {
            $p = Get-Process -Id $proc.Id -ErrorAction Stop
            $peakWs = [math]::Max($peakWs, $p.WorkingSet64)
            $peakPb = [math]::Max($peakPb, $p.PrivateMemorySize64)
        } catch {}
        Start-Sleep -Milliseconds 10
    }
    $proc.WaitForExit(); $sw.Stop()
    $row = [ordered]@{
        implementation = $Mode; run = $Run; exit_code = $proc.ExitCode
        wall_ms = [math]::Round($sw.Elapsed.TotalMilliseconds, 1)
        peak_working_set_bytes = $peakWs; peak_private_bytes = $peakPb
    }
    foreach ($m in [regex]::Matches((Get-Content $out -Raw), "(?m)^JX_METRIC\s+(\w+)=(-?[0-9]+)\s*$")) {
        $row[$m.Groups[1].Value] = [long]$m.Groups[2].Value
    }
    if (-not $row.Contains("heap_live_after_gc_bytes")) {
        Write-Warning "$Mode run $Run did not finish: $(Get-Content $err -Raw)"
    }
    Remove-Item $out, $err -Force -ErrorAction SilentlyContinue
    [pscustomobject]$row
}

$results = [System.Collections.Generic.List[object]]::new()
for ($r = 1; $r -le $Runs; $r++) {
    foreach ($mode in "javafx", "jxparallel") {
        Write-Host "[$r/$Runs] $mode..."
        $results.Add((Invoke-Case $mode $r))
    }
}

$columns = $results | ForEach-Object { $_.PSObject.Properties.Name } | Select-Object -Unique
$results | Select-Object $columns | Export-Csv -Path $Output -NoTypeInformation -Encoding UTF8
$summary = foreach ($column in $columns | Where-Object { $_ -notin "implementation", "run" }) {
    $line = [ordered]@{ metric = $column }
    foreach ($mode in "javafx", "jxparallel") {
        $values = @($results | Where-Object implementation -eq $mode | ForEach-Object { $_.$column } |
                    Where-Object { $_ -ne $null } | Sort-Object)
        $line[$mode] = if ($values.Count) { $values[[int][math]::Floor(($values.Count - 1) / 2)] } else { $null }
    }
    [pscustomobject]$line
}
$summaryPath = [IO.Path]::ChangeExtension($Output, $null).TrimEnd('.') + "-median.csv"
$summary | Export-Csv -Path $summaryPath -NoTypeInformation -Encoding UTF8
$summary | Format-Table -AutoSize
Write-Host "Saved $Output and $summaryPath"
