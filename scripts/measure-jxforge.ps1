param(
    [int]$Runs = 10,
    [int]$Passes = 10,
    [string]$Date = (Get-Date -Format "yyyy-MM-dd"),
    [string[]]$Jvms = @("java8-x86", "java17-x64", "java21-x64"),
    [string[]]$Stages = @("load", "ready"),
    [string[]]$Modes = @("javafx", "interpreted", "template", "compiled", "compiled-plain")
)

# FXML (FxmlLoadBenchmark, javafx mode) against the JXML builds of the same 20 screens
# (ForgeLoadBenchmark), each run in a fresh JVM, modes alternating within every run.
# Stage "load": FXMLLoader.load vs building the element tree. Stage "ready": plus Scene,
# applyCss and layout vs native nodes laid out at 1280x800 with the other tabs mounted ahead.
#
# Build first (JavaFX examples on JDK 17, the rest on any JDK 8+):
#   mvn -pl jxparallel-examples-native -am install -DskipTests
#   mvn -P legacy-javafx -pl jxparallel-examples -am compile        (JAVA_HOME = JDK 17)
#   mvn -pl jxparallel-examples-native dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
# ponytail: process sampling duplicated from measure-fxml.ps1; a third copy would justify a module.

$ErrorActionPreference = "Stop"
[System.Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::InvariantCulture
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$fxRepo = Join-Path $env:USERPROFILE ".m2\repository\org\openjfx"
$openJfx = (@('javafx-base', 'javafx-graphics', 'javafx-controls', 'javafx-fxml') |
            ForEach-Object { "$fxRepo\$_\21.0.2\$_-21.0.2-win.jar" }) -join ';'
$fxCp = "jxparallel-examples\target\classes;jxparallel-fxml\target\classes;jxparallel-javafx\target\classes;jxparallel-core\target\classes"
$deps = (Get-Content "jxparallel-examples-native\target\classpath.txt" -Raw).Trim().Split(';')
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

function Invoke-Case([hashtable]$Jvm, [string]$Stage, [string]$Mode, [int]$Run) {
    $ready = if ($Stage -eq "ready") { "true" } else { "false" }
    if ($Mode -eq "javafx") {
        $jvmArgs = @("-Djx.fxml.passes=$Passes", "-Djx.fxml.ready=$ready") + $Jvm.fx +
                   @("-cp", "`"$fxCp`"", "com.jxparallel.examples.FxmlLoadBenchmark", "javafx")
    } else {
        $jvmArgs = @("-Djx.forge.passes=$Passes", "-Djx.forge.ready=$ready", "-cp", "`"$($Jvm.cp)`"",
                     "com.jxparallel.examples.nativeui.ForgeLoadBenchmark", $Mode)
    }
    $out = Join-Path $env:TEMP "jxforge-$Mode-$Run-out.txt"
    $err = Join-Path $env:TEMP "jxforge-$Mode-$Run-err.txt"
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $proc = Start-Process -FilePath $Jvm.java -ArgumentList $jvmArgs -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -WindowStyle Hidden
    $peakWs = 0L; $peakPb = 0L
    while (-not $proc.HasExited) {
        try {
            $p = Get-Process -Id $proc.Id -ErrorAction Stop
            $peakWs = [math]::Max($peakWs, $p.WorkingSet64)
            $peakPb = [math]::Max($peakPb, $p.PrivateMemorySize64)
        } catch {}
        Start-Sleep -Milliseconds 10
        if ($sw.Elapsed.TotalMinutes -gt 5) { $proc.Kill(); Write-Warning "$Mode run $Run killed after 5 min" }
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

foreach ($jvmName in $Jvms) {
    $jvm = $jvmTable[$jvmName]
    foreach ($stage in $Stages) {
        $results = [System.Collections.Generic.List[object]]::new()
        for ($r = 1; $r -le $Runs; $r++) {
            foreach ($mode in $Modes) {
                Write-Host "[$jvmName $stage $r/$Runs] $mode..."
                $results.Add((Invoke-Case $jvm $stage $mode $r))
            }
        }
        $output = "docs\jxforge-results-$Date-$jvmName-$stage.csv"
        $columns = $results | ForEach-Object { $_.PSObject.Properties.Name } | Select-Object -Unique
        $results | Select-Object $columns | Export-Csv -Path $output -NoTypeInformation -Encoding UTF8
        $summary = foreach ($column in $columns | Where-Object { $_ -notin "implementation", "run" }) {
            $line = [ordered]@{ metric = $column }
            foreach ($mode in $Modes) {
                $values = @($results | Where-Object implementation -eq $mode | ForEach-Object { $_.$column } |
                            Where-Object { $_ -ne $null } | Sort-Object)
                $line[$mode] = if ($values.Count) { $values[[int][math]::Floor(($values.Count - 1) / 2)] } else { $null }
                $line["$mode-min"] = if ($values.Count) { $values[0] } else { $null }
                $line["$mode-max"] = if ($values.Count) { $values[-1] } else { $null }
            }
            [pscustomobject]$line
        }
        $summary | Export-Csv -Path ($output -replace '\.csv$', '-median.csv') -NoTypeInformation -Encoding UTF8
        Write-Host "Saved $output"
    }
}
