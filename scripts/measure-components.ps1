param(
    [int]$Runs = 5,
    [int]$Passes = 5,
    [string]$Date = (Get-Date -Format "yyyy-MM-dd"),
    [string[]]$Jvms = @("java8-x86", "java17-x64", "java21-x64"),
    [string[]]$Components = @(),
    [string]$Out = "",
    [string[]]$Modes = @("javafx", "interpreted", "template", "compiled", "live"),
    [string]$FxPrism = "",
    [int]$TimeoutSec = 300,
    [switch]$Force,
    [switch]$IncludeAliases
)

# One component at a time: its JavaFX counterpart (ComponentBenchFx, when it has one) against the JXML modes
# (ComponentBench: interpreted, template, compiled and, on the live-bindings research branch, live), each run in a fresh JVM, the modes alternating their place within
# every run, components in the outer loop. Stages inside every JVM: build, ready, update, paint, retained (see the
# classes). Per JVM one CSV with a row per run, and -median.csv with the median, min and max per component and mode.
# Resumable: components that already have all their rows are skipped unless -Force. A mode that exits non-zero or times
# out is logged, its row kept with the exit code, and the rest of its runs skipped; the series goes on.
#
# Build first (JavaFX examples on JDK 17, the rest on any JDK 8+):
#   mvn -pl jxparallel-forge,jxparallel-examples-native -am install -DskipTests
#   mvn -P legacy-javafx -pl jxparallel-examples -am compile        (JAVA_HOME = JDK 17)
#   mvn -pl jxparallel-examples-native dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
# Examples:
#   scripts\measure-components.ps1 -Runs 1 -Passes 3 -Jvms java17-x64 -Components Button,TextField,ListView,Row -Out $env:TEMP\comp
#   scripts\measure-components.ps1 -FxPrism sw ...    (JavaFX painted by its software pipeline, -Dprism.order=sw)
# The fixed heap (-Xms512m -Xmx512m) is the same for every JVM and mode, so the working set and the collector's work do
# not depend on the RAM of the machine.

$ErrorActionPreference = "Stop"
# `powershell -File` hands "a,b" over as one string: split lists on commas
$Components = @($Components | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
$Jvms = @($Jvms | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
$Modes = @($Modes | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
[System.Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::InvariantCulture
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
if (-not $Out) { $Out = Join-Path $root "docs\components-$Date" }
New-Item -ItemType Directory -Force $Out | Out-Null

$fxRepo = Join-Path $env:USERPROFILE ".m2\repository\org\openjfx"
$openJfx = (@('javafx-base', 'javafx-graphics', 'javafx-controls', 'javafx-fxml') |
            ForEach-Object { "$fxRepo\$_\21.0.2\$_-21.0.2-win.jar" }) -join ';'
$fxCp = "jxparallel-examples\target\classes;jxparallel-fxml\target\classes;jxparallel-javafx\target\classes;jxparallel-core\target\classes"
# The third-party jars of the classpath file only: its jxparallel-* entries are the jars installed in ~/.m2, which may be
# another version of the code than this tree's. The modules come from their target\classes (live-bindings research branch).
$deps = @((Get-Content "jxparallel-examples-native\target\classpath.txt" -Raw).Trim().Split(';') |
          Where-Object { (Split-Path $_ -Leaf) -notlike 'jxparallel-*' })
$deps = @("jxparallel-ui\target\classes", "jxparallel-forge\target\classes", "jxparallel-core\target\classes") + $deps
$deps86 = $deps | ForEach-Object {
    $alt = $_ -replace 'natives-windows\.jar$', 'natives-windows-x86.jar'
    if ($alt -ne $_ -and (Test-Path $alt)) { $alt } else { $_ }
}
$nativeCp64 = "jxparallel-examples-native\target\classes;" + ($deps -join ';')
$nativeCp86 = "jxparallel-examples-native\target\classes;" + ($deps86 -join ';')

$jvmTable = @{
    "java8-x86"  = @{ java = "C:\Program Files (x86)\Java\jdk1.8.0_51\bin\java.exe"; fx = @(); cp = $nativeCp86 }
    "java17-x64" = @{ java = "C:\Program Files\Java\jdk-17\bin\java.exe"
                      fx = @("--module-path", "`"$openJfx`"", "--add-modules", "javafx.controls,javafx.fxml"); cp = $nativeCp64 }
    "java21-x64" = @{ java = "C:\Program Files\Java\jdk-21\bin\java.exe"
                      fx = @("--module-path", "`"$openJfx`"", "--add-modules", "javafx.controls,javafx.fxml"); cp = $nativeCp64 }
}
$heapFlags = @("-Xms512m", "-Xmx512m")

# ---- the components: one table for both runners ------------------------------------------------------------------
$nativeTable = "jxparallel-examples-native\src\main\resources\componentbench\components.csv"
$fxTable = "jxparallel-examples\src\main\resources\componentbench\components.csv"
if ((Get-FileHash $nativeTable).Hash -ne (Get-FileHash $fxTable).Hash) {
    throw "components.csv differs between the two modules: run scripts/generate-component-bench.py and rebuild"
}
$table = @(Import-Csv $nativeTable)
$selected = @($table | Where-Object {
    $name = $_.name
    if ($Components.Count -gt 0) { ($Components | Where-Object { $name -like $_ }).Count -gt 0 }
    else { $IncludeAliases -or -not $_.alias_of }
})
if ($selected.Count -eq 0) { throw "no component matches $($Components -join ', ')" }

# ---- pre-flight: machine, JDKs, background load -----------------------------------------------------------------------
function Get-JavaVersion([string]$java) {
    $err = Join-Path $env:TEMP "jxcomp-version-$PID.txt"
    $outf = Join-Path $env:TEMP "jxcomp-version-out-$PID.txt"
    $p = Start-Process -FilePath $java -ArgumentList "-version" -RedirectStandardError $err -RedirectStandardOutput $outf -PassThru -WindowStyle Hidden -Wait
    $text = (Get-Content $err -Raw) -replace "\r?\n", " | "
    Remove-Item $err, $outf -Force -ErrorAction SilentlyContinue
    $text
}
$samples = @()
for ($i = 0; $i -lt 5; $i++) {
    $cpu = Get-CimInstance Win32_PerfFormattedData_PerfOS_Processor -Filter "Name='_Total'"
    $samples += [double]$cpu.PercentProcessorTime
    Start-Sleep -Seconds 1
}
$cs = Get-CimInstance Win32_ComputerSystem
$proc = @(Get-CimInstance Win32_Processor)[0]
$os = Get-CimInstance Win32_OperatingSystem
$env_ = [ordered]@{
    date = $Date; machine = "$($cs.Manufacturer) $($cs.Model)"; cpu = $proc.Name.Trim(); cores = $proc.NumberOfCores
    logical_processors = $proc.NumberOfLogicalProcessors; ram_gb = [math]::Round($cs.TotalPhysicalMemory / 1GB, 1)
    os = "$($os.Caption) $($os.Version)"; powershell = $PSVersionTable.PSVersion.ToString()
    background_cpu_percent_mean = [math]::Round(($samples | Measure-Object -Average).Average, 1)
    background_cpu_percent_max = ($samples | Measure-Object -Maximum).Maximum
    heap_flags = ($heapFlags -join ' '); runs = $Runs; passes = $Passes; modes = ($Modes -join ','); fx_prism_order = $(if ($FxPrism) { $FxPrism } else { "default" })
    timeout_sec = $TimeoutSec; components = $selected.Count
    jvms = [ordered]@{}
}
foreach ($j in $Jvms) { $env_.jvms[$j] = [ordered]@{ java = $jvmTable[$j].java; version = (Get-JavaVersion $jvmTable[$j].java) } }
$env_ | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $Out "environment.json") -Encoding UTF8
Write-Host ("Background CPU before the series: mean {0}%, max {1}%" -f $env_.background_cpu_percent_mean, $env_.background_cpu_percent_max)
if ($env_.background_cpu_percent_mean -gt 10) { Write-Warning "the machine is not idle: the times of this series are noisy" }

# ---- one run of one mode -----------------------------------------------------------------------------------------------
function Invoke-Case([hashtable]$Jvm, [pscustomobject]$Comp, [string]$Mode, [int]$Run) {
    $heap = $heapFlags + @("-Djx.comp.passes=$Passes")
    if ($Mode -eq "javafx") {
        $prism = if ($FxPrism) { @("-Dprism.order=$FxPrism") } else { @() }
        $jvmArgs = $heap + $prism + $Jvm.fx + @("-cp", "`"$fxCp`"", "com.jxparallel.examples.ComponentBenchFx", "javafx", $Comp.name)
    } else {
        $jvmArgs = $heap + @("-cp", "`"$($Jvm.cp)`"", "com.jxparallel.examples.nativeui.ComponentBench", $Mode, $Comp.name)
    }
    $tag = "$PID-$($Comp.name)-$Mode-$Run"
    $out = Join-Path $env:TEMP "jxcomp-$tag-out.txt"
    $err = Join-Path $env:TEMP "jxcomp-$tag-err.txt"
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $p = Start-Process -FilePath $Jvm.java -ArgumentList $jvmArgs -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -WindowStyle Hidden
    $null = $p.Handle  # keeps the process handle, so ExitCode is still there after it has exited
    $peakWs = 0L; $peakPb = 0L; $timedOut = $false
    while (-not $p.HasExited) {
        try {
            $q = Get-Process -Id $p.Id -ErrorAction Stop
            $peakWs = [math]::Max($peakWs, $q.WorkingSet64)
            $peakPb = [math]::Max($peakPb, $q.PrivateMemorySize64)
        } catch {}
        Start-Sleep -Milliseconds 10
        if ($sw.Elapsed.TotalSeconds -gt $TimeoutSec) { $timedOut = $true; try { $p.Kill() } catch {}; break }
    }
    $p.WaitForExit(); $sw.Stop()
    $row = [ordered]@{
        component = $Comp.name; group = $Comp.group; tag = $Comp.tag; mode = $Mode; run = $Run
        exit_code = $(if ($timedOut) { -1 } else { $p.ExitCode }); status = $(if ($timedOut) { "timeout" } elseif ($p.ExitCode -eq 0) { "ok" } else { "failed" })
        wall_ms = [math]::Round($sw.Elapsed.TotalMilliseconds, 1); peak_working_set_bytes = $peakWs; peak_private_bytes = $peakPb
    }
    $text = if (Test-Path $out) { Get-Content $out -Raw } else { "" }
    foreach ($m in [regex]::Matches($text, "(?m)^JX_METRIC\s+(\w+)=(-?[0-9]+)\s*$")) { $row[$m.Groups[1].Value] = [long]$m.Groups[2].Value }
    if ($Mode -ne "verify" -and -not $row.Contains("heap_live_released_bytes") -and $row.status -eq "ok") { $row.status = "incomplete" }
    if ($row.status -ne "ok") {
        $tail = ""
        if (Test-Path $err) { $content = Get-Content $err -Raw; if ($content) { $tail = $content } }
        Write-Warning ("{0} {1} run {2}: {3} (exit {4}) {5}" -f $Comp.name, $Mode, $Run, $row.status, $row.exit_code, $tail.Substring(0, [math]::Min(300, $tail.Length)))
    }
    Remove-Item $out, $err -Force -ErrorAction SilentlyContinue
    [pscustomobject]$row
}

# ---- fingerprints: every row is compared with the reference of its component (the verify run of the same JVM) --------------
function Set-Verified([System.Collections.Generic.List[object]]$Rows, [string]$Component) {
    $ref = $Rows | Where-Object { $_.component -eq $Component -and $_.mode -eq "verify" } | Select-Object -First 1
    foreach ($r in ($Rows | Where-Object { $_.component -eq $Component })) {
        $ok = 0; $nodes = ""
        if ($r.mode -eq "verify") { $ok = [int]($r.status -eq "ok" -and $r.verified -eq 1) }
        elseif ($ref -and $ref.status -eq "ok" -and $ref.verified -eq 1 -and $r.status -eq "ok") {
            $ok = [int]($r.fp_instances -eq $ref.fp_instances -and $r.fp_probe -eq $ref.fp_probe -and $r.fp_probe_n -eq $ref.fp_probe_n -and
                        $r.fp_probe_updated -eq $ref.fp_probe_updated -and $r.fp_probe_updated_n -eq $ref.fp_probe_updated_n)
            $nodes = [int]($r.fp_nodes -eq $ref.fp_nodes)
        }
        $r | Add-Member -NotePropertyName verified_all -NotePropertyValue $ok -Force
        $r | Add-Member -NotePropertyName fp_nodes_match -NotePropertyValue $nodes -Force
    }
}

function Get-Median([double[]]$v) {
    $s = @($v | Sort-Object)
    if ($s.Count -eq 0) { return $null }
    if ($s.Count % 2) { $s[($s.Count - 1) / 2] } else { ($s[$s.Count / 2 - 1] + $s[$s.Count / 2]) / 2 }
}

function Write-Results([System.Collections.Generic.List[object]]$Rows, [string]$Csv) {
    $columns = @($Rows | ForEach-Object { $_.PSObject.Properties.Name } | Select-Object -Unique)
    $Rows | Select-Object $columns | Export-Csv -Path $Csv -NoTypeInformation -Encoding UTF8
    $skip = @("component", "group", "tag", "mode", "run", "status", "verified_all", "fp_nodes_match", "exit_code", "verified", "jvm", "pipeline")
    $summary = foreach ($pair in ($Rows | Where-Object { $_.mode -ne "verify" } | Group-Object { "$($_.component)|$($_.mode)" })) {
        $ok = @($pair.Group | Where-Object { $_.status -eq "ok" })
        $first = $pair.Group[0]
        $line = [ordered]@{ component = $first.component; group = $first.group; tag = $first.tag; mode = $first.mode
                            runs = $pair.Group.Count; runs_ok = $ok.Count
                            runs_verified = @($ok | Where-Object { $_.verified_all -eq 1 }).Count }
        foreach ($c in $columns | Where-Object { $_ -notin $skip }) {
            $v = @()
            foreach ($x in $ok) {
                $d = 0.0
                if ([double]::TryParse([string]$x.$c, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$d)) { $v += $d }
            }
            if ($v.Count) {
                $line[$c] = Get-Median $v
                $line["${c}_min"] = ($v | Measure-Object -Minimum).Minimum
                $line["${c}_max"] = ($v | Measure-Object -Maximum).Maximum
            }
        }
        [pscustomobject]$line
    }
    $med = @($summary)
    $mcolumns = @($med | ForEach-Object { $_.PSObject.Properties.Name } | Select-Object -Unique)
    $med | Select-Object $mcolumns | Export-Csv -Path ($Csv -replace '\.csv$', '-median.csv') -NoTypeInformation -Encoding UTF8
}

# ---- the series ------------------------------------------------------------------------------------------------------------
foreach ($jvmName in $Jvms) {
    $jvm = $jvmTable[$jvmName]
    $csv = Join-Path $Out "components-$jvmName.csv"
    $rows = [System.Collections.Generic.List[object]]::new()
    if ((Test-Path $csv) -and -not $Force) {
        foreach ($r in (Import-Csv $csv)) { $rows.Add($r) }
    } elseif ((Test-Path $csv) -and $Force) {
        foreach ($r in (Import-Csv $csv)) { if ($r.component -notin $selected.name) { $rows.Add($r) } }
    }
    $index = 0
    foreach ($comp in $selected) {
        $index++
        $compModes = @($Modes | Where-Object { $_ -ne "javafx" -or $comp.fx }) # not $modes: PowerShell names are case-insensitive and that would overwrite the parameter
        $have = @($rows | Where-Object { $_.component -eq $comp.name })
        $expected = $compModes.Count * $Runs + 1
        if ($have.Count -ge $expected -and -not $Force) { Write-Host "[$jvmName $index/$($selected.Count)] $($comp.name): done, skipped"; continue }
        foreach ($r in $have) { [void]$rows.Remove($r) }
        Write-Host "[$jvmName $index/$($selected.Count)] $($comp.name) (n=$($comp.n), $($compModes -join '/'))..."
        $dead = @{}
        $verify = Invoke-Case $jvm $comp "verify" 0
        $verify | Add-Member -NotePropertyName jvm -NotePropertyValue $jvmName -Force
        $rows.Add($verify)
        if ($verify.status -ne "ok") { Write-Warning "$($comp.name): the three ways of building it do NOT agree (verify failed); measuring anyway" }
        for ($r = 1; $r -le $Runs; $r++) {
            $rot = @()
            for ($i = 0; $i -lt $compModes.Count; $i++) { $rot += $compModes[($i + $r - 1) % $compModes.Count] }  # the first mode changes every run
            foreach ($mode in $rot) {
                if ($dead.ContainsKey($mode)) { continue }
                $row = Invoke-Case $jvm $comp $mode $r
                $row | Add-Member -NotePropertyName jvm -NotePropertyValue $jvmName -Force
                $row | Add-Member -NotePropertyName pipeline -NotePropertyValue $(if ($mode -eq "javafx" -and $FxPrism) { $FxPrism } else { "" }) -Force
                $rows.Add($row)
                if ($row.status -ne "ok") { $dead[$mode] = $true }
            }
        }
        Set-Verified $rows $comp.name
        $bad = @($rows | Where-Object { $_.component -eq $comp.name -and $_.mode -ne "verify" -and $_.status -eq "ok" -and $_.verified_all -ne 1 })
        if ($bad.Count) { Write-Warning ("{0}: {1} run(s) whose fingerprint differs from the reference (verified_all=0)" -f $comp.name, $bad.Count) }
        Write-Results $rows $csv
    }
    if ($rows.Count) { Write-Results $rows $csv; Write-Host "Saved $csv" }
}
