param(
    [string]$JavaPath = "java",
    [string]$NativeClasspath = "jxparallel-examples-native\target\classes;jxparallel-ui\target\classes;jxparallel-core\target\classes",
    [string]$NativeDependencies,
    [string]$JavaFxClasspath,
    [string]$JavaFxModulePath,
    [int]$Runs = 5,
    [string]$Output = "ui-metrics.csv"
)

$ErrorActionPreference = "Stop"
[System.Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::InvariantCulture
[System.Threading.Thread]::CurrentThread.CurrentUICulture = [Globalization.CultureInfo]::InvariantCulture

function Get-MetricValue([string]$OutputText, [string]$Name) {
    $match = [regex]::Match($OutputText, "(?m)^JX_METRIC\s+$([regex]::Escape($Name))=([0-9-]+)\s*$")
    if ($match.Success) {
        return [long]$match.Groups[1].Value
    }
    return $null
}

function Invoke-UiCase([string]$Name, [string]$Classpath, [string]$MainClass, [int]$RunNumber) {
    $stdout = Join-Path $env:TEMP ("jxparallel-ui-$Name-$RunNumber-out.txt")
    $stderr = Join-Path $env:TEMP ("jxparallel-ui-$Name-$RunNumber-err.txt")
    Remove-Item $stdout, $stderr -Force -ErrorAction SilentlyContinue

    $started = [System.Diagnostics.Stopwatch]::StartNew()
    $arguments = @()
    if ($Name -eq "javafx" -and -not [string]::IsNullOrWhiteSpace($JavaFxModulePath)) {
        $arguments += "--module-path"
        $arguments += ('"' + $JavaFxModulePath + '"')
        $arguments += "--add-modules"
        $arguments += "javafx.controls"
    }
    $arguments += "-cp"
    $arguments += ('"' + $Classpath + '"')
    $arguments += $MainClass
    $process = Start-Process -FilePath $JavaPath `
        -ArgumentList $arguments `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru

    $samples = New-Object System.Collections.Generic.List[object]
    while (-not $process.HasExited) {
        try {
            $sampleProcess = Get-Process -Id $process.Id -ErrorAction Stop
            $samples.Add([pscustomobject]@{
                TimeMs = $started.Elapsed.TotalMilliseconds
                WorkingSet = [int64]$sampleProcess.WorkingSet64
                PrivateBytes = [int64]$sampleProcess.PrivateMemorySize64
                CpuMs = $sampleProcess.CPU * 1000.0
            })
        } catch {
        }
        Start-Sleep -Milliseconds 10
    }
    $process.WaitForExit()
    $process.Refresh()
    $exitCode = $process.ExitCode
    $started.Stop()

    $outputText = if (Test-Path $stdout) { Get-Content $stdout -Raw } else { "" }
    $errorText = if (Test-Path $stderr) { Get-Content $stderr -Raw } else { "" }
    $firstPaint = Get-MetricValue $outputText "first_paint_ns"
    $interactionStart = Get-MetricValue $outputText "interaction_start_ns"
    $interactionEnd = Get-MetricValue $outputText "interaction_end_ns"
    $processStart = Get-MetricValue $outputText "process_start_ns"
    $cpuNanos = Get-MetricValue $outputText "process_cpu_ns"
    $heapDelta = Get-MetricValue $outputText "heap_delta_bytes"
    $threadDelta = Get-MetricValue $outputText "thread_delta"

    if ($samples.Count -eq 0) {
        throw "$Name run $RunNumber produced no process samples. stderr: $errorText"
    }

    $positiveSamples = @($samples | Where-Object { $_.WorkingSet -gt 0 })
    if ($positiveSamples.Count -eq 0) {
        throw "$Name run $RunNumber produced no positive memory samples."
    }
    $firstSample = $positiveSamples[0]
    $lastSample = $positiveSamples[$positiveSamples.Count - 1]
    $peakWorkingSet = ($samples | Measure-Object WorkingSet -Maximum).Maximum
    $peakPrivateBytes = ($samples | Measure-Object PrivateBytes -Maximum).Maximum
    $averageWorkingSet = ($samples | Measure-Object WorkingSet -Average).Average
    $sampleCpuDelta = $lastSample.CpuMs - $firstSample.CpuMs
    $wallMs = $started.Elapsed.TotalMilliseconds
    $cpuMs = if ($cpuNanos -ne $null) { $cpuNanos / 1000000.0 } else { $sampleCpuDelta }
    $cpuPercent = if ($wallMs -gt 0) { 100.0 * $cpuMs / $wallMs / [Environment]::ProcessorCount } else { 0.0 }
    $startupMs = if ($firstPaint -ne $null -and $processStart -ne $null) {
        ($firstPaint - $processStart) / 1000000.0
    } else { $null }
    $interactionMs = if ($interactionStart -ne $null -and $interactionEnd -ne $null) {
        ($interactionEnd - $interactionStart) / 1000000.0
    } else { $null }

    Remove-Item $stdout, $stderr -Force -ErrorAction SilentlyContinue
    return [pscustomobject]@{
        implementation = $Name
        run = $RunNumber
        exit_code = if ($exitCode -ne $null) { [int]$exitCode } else { -1 }
        wall_ms = [math]::Round($wallMs, 3)
        startup_to_first_paint_ms = if ($startupMs -ne $null) { [math]::Round($startupMs, 3) } else { $null }
        interaction_ms = if ($interactionMs -ne $null) { [math]::Round($interactionMs, 3) } else { $null }
        process_cpu_ms = [math]::Round($cpuMs, 3)
        normalized_cpu_percent = [math]::Round($cpuPercent, 3)
        peak_working_set_bytes = $peakWorkingSet
        average_working_set_bytes = [math]::Round($averageWorkingSet, 0)
        peak_private_bytes = $peakPrivateBytes
        final_working_set_bytes = $lastSample.WorkingSet
        heap_delta_bytes = $heapDelta
        thread_delta = $threadDelta
    }
}

if ([string]::IsNullOrWhiteSpace($JavaFxClasspath)) {
    throw "Provide -JavaFxClasspath with the compiled JavaFX example, core, bridge, and OpenJFX jars."
}

$nativeRuntimeClasspath = $NativeClasspath
if (-not [string]::IsNullOrWhiteSpace($NativeDependencies)) {
    $nativeRuntimeClasspath += ";" + $NativeDependencies
}

$results = New-Object System.Collections.Generic.List[object]
for ($run = 1; $run -le $Runs; $run++) {
    $results.Add((Invoke-UiCase "javafx" $JavaFxClasspath "com.jxparallel.examples.JavaFxMetricsRunner" $run))
    $results.Add((Invoke-UiCase "jxparallel-native" $nativeRuntimeClasspath "com.jxparallel.examples.nativeui.NativeMetricsRunner" $run))
}

$results | Export-Csv -Path $Output -NoTypeInformation -Encoding UTF8
$results | Format-Table -AutoSize
Write-Output "Saved metrics to $Output"
