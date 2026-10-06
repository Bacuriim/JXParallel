param(
    [string]$JavaPath   = "C:\Program Files (x86)\Java\jdk1.8.0_51\bin\java.exe",
    [string]$Classpath,                 # target\bench;jxparallel-fx jar;its runtime dependencies
    [string]$PureFxClasspath = "target\purefx", # PureFxBench: the same app on plain JavaFX (no JX)
    [string]$Java17Path = "C:\Program Files\Java\jdk-17\bin\java.exe", # Compose Desktop needs Java 11+ (64 bits)
    [string]$ComposeClasspath = "",              # ComposeBenchKt: the same app in Compose Desktop (Kotlin)
    [string]$SwtClasspath = "target\swt",       # SwtBench: the same app in SWT (plus the SWT jar)
    [string]$SwingClasspath = "target\swing",   # SwingBench: the same app in Swing ("flatlaf": plus the FlatLaf jar)
    [int]$Runs          = 5,
    [string]$Output     = "docs\fx-backends-results.csv",
    [string[]]$Cases    = @("javafx", "native"),
    [string[]]$ExtraArgs = @()
)

# Runs FxBackendBench (scripts/FxBackendBench.java) alternately on the JavaFX backend and the native
# backend (-Djx.backend=native), and optionally ("purefx") PureFxBench, the same application with
# plain JavaFX imports (generated from FxBackendBench by swapping the imports), a fresh JVM per run. Every "JX_METRIC key=value" line becomes a CSV
# column; process memory (working set, private bytes) is sampled outside the JVM every 10 ms.

$ErrorActionPreference = "Stop"
[System.Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::InvariantCulture

function Invoke-Case([string]$Name, [int]$Run) {
    $out = Join-Path $env:TEMP "jxfx-$Name-$Run-out.txt"
    $err = Join-Path $env:TEMP "jxfx-$Name-$Run-err.txt"
    Remove-Item $out, $err -Force -ErrorAction SilentlyContinue
    $jvmArgs = [System.Collections.Generic.List[string]]::new()
    if ($Name -eq "native") { $jvmArgs.Add("-Djx.backend=native") }
    # the native backend painted on the CPU and shown with GDI: no OpenGL context
    if ($Name -eq "software") { $jvmArgs.Add("-Djx.backend=native"); $jvmArgs.Add("-Djx.renderer=software") }
    # the same, with the heap given back to the system after collections
    if ($Name -eq "softwarelean") {
        $jvmArgs.Add("-Djx.backend=native"); $jvmArgs.Add("-Djx.renderer=software")
        $jvmArgs.Add("-XX:MaxHeapFreeRatio=20"); $jvmArgs.Add("-XX:MinHeapFreeRatio=5")
    }
    foreach ($a in $ExtraArgs) { $jvmArgs.Add($a) }
    $java = $JavaPath
    if ($Name -eq "compose") {
        $java = $Java17Path
        $jvmArgs.Add("-cp"); $jvmArgs.Add("`"$ComposeClasspath`""); $jvmArgs.Add("ComposeBenchKt")
    } elseif ($Name -eq "swing17") { # Swing on Compose's JVM: what is the JVM's and what is the toolkit's
        $java = $Java17Path
        $jvmArgs.Add("-cp"); $jvmArgs.Add("`"$SwingClasspath`""); $jvmArgs.Add("SwingBench")
    } elseif ($Name -eq "swt") {
        $jvmArgs.Add("-cp"); $jvmArgs.Add("`"$SwtClasspath`""); $jvmArgs.Add("SwtBench")
    } elseif ($Name -eq "swing" -or $Name -eq "flatlaf") {
        if ($Name -eq "flatlaf") { $jvmArgs.Add("-Dbench.laf=com.formdev.flatlaf.FlatLightLaf") }
        $jvmArgs.Add("-cp"); $jvmArgs.Add("`"$SwingClasspath`""); $jvmArgs.Add("SwingBench")
    } elseif ($Name -eq "purefx") {
        $jvmArgs.Add("-cp"); $jvmArgs.Add("`"$PureFxClasspath`""); $jvmArgs.Add("PureFxBench")
    } else {
        $jvmArgs.Add("-cp"); $jvmArgs.Add("`"$Classpath`""); $jvmArgs.Add("FxBackendBench")
    }

    $sw   = [System.Diagnostics.Stopwatch]::StartNew()
    $proc = Start-Process -FilePath $java -ArgumentList $jvmArgs.ToArray() `
                -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    $peakWs = 0L; $peakPb = 0L
    while (-not $proc.HasExited) {
        try {
            $p = Get-Process -Id $proc.Id -ErrorAction Stop
            $peakWs = [math]::Max($peakWs, $p.WorkingSet64)
            $peakPb = [math]::Max($peakPb, $p.PrivateMemorySize64)
        } catch {}
        Start-Sleep -Milliseconds 10
        if ($sw.Elapsed.TotalSeconds -gt 300) { Stop-Process -Id $proc.Id -Force; break }
    }
    $proc.WaitForExit(); $sw.Stop()
    $row = [ordered]@{
        backend                = $Name
        run                    = $Run
        exit_code              = $proc.ExitCode
        wall_ms                = [math]::Round($sw.Elapsed.TotalMilliseconds, 1)
        peak_working_set_bytes = $peakWs
        peak_private_bytes     = $peakPb
    }
    $txt = if (Test-Path $out) { Get-Content $out -Raw } else { "" }; if ($null -eq $txt) { $txt = "" }
    foreach ($m in [regex]::Matches($txt, "(?m)^JX_METRIC\s+(\w+)=(-?[0-9]+)\s*$")) {
        $row[$m.Groups[1].Value] = [long]$m.Groups[2].Value
    }
    if (-not $row.Contains("heap_live_after_gc_bytes")) {
        Write-Warning "$Name run $Run did not finish; stderr: $(Get-Content $err -Raw -ErrorAction SilentlyContinue)"
    }
    Remove-Item $out, $err -Force -ErrorAction SilentlyContinue
    return [pscustomobject]$row
}

$results = [System.Collections.Generic.List[object]]::new()
for ($r = 1; $r -le $Runs; $r++) {
    foreach ($case in $Cases) {
        Write-Host "[$r/$Runs] $case..."
        $results.Add((Invoke-Case $case $r))
    }
}
$columns = $results | ForEach-Object { $_.PSObject.Properties.Name } | Select-Object -Unique
$results | Select-Object $columns | Export-Csv -Path $Output -NoTypeInformation -Encoding UTF8
$summary = foreach ($column in $columns | Where-Object { $_ -notin "backend", "run" }) {
    $line = [ordered]@{ metric = $column }
    foreach ($b in $Cases) {
        $values = @($results | Where-Object backend -eq $b | ForEach-Object { $_.$column } |
                    Where-Object { $_ -ne $null } | Sort-Object)
        $line[$b] = if ($values.Count) { $values[[int][math]::Floor(($values.Count - 1) / 2)] } else { $null }
    }
    [pscustomobject]$line
}
$summaryPath = [IO.Path]::ChangeExtension($Output, $null).TrimEnd('.') + "-median.csv"
$summary | Export-Csv -Path $summaryPath -NoTypeInformation -Encoding UTF8
$summary | Format-Table -AutoSize
Write-Host "Saved $Output and $summaryPath"

