param(
    [string[]]$Components = @("Separator", "Slider", "ScrollBar", "ProgressBar", "Image", "TableView", "ListView", "Tree", "TreeTable", "Label", "Button", "TextField", "Grid"),
    [int]$Runs = 5,
    [int]$Rounds = 400,
    [string]$Out = "docs\research\live-2026-10-26",
    [string]$Java = "C:\Program Files\Java\jdk-17\bin\java.exe"
)

# Live-bindings research: the update of each component in steady state (LiveSteadyBench), compiled and live side by side,
# each run in a fresh JVM (fixed 512 MB heap, like measure-components.ps1); the median of the runs. Writes steady-runs.csv
# (a row per run) and steady-median.csv. Needs the modules built (mvn -o -pl jxparallel-core,jxparallel-ui,jxparallel-forge,
# jxparallel-examples-native compile) and jxparallel-examples-native\target\classpath.txt.
$ErrorActionPreference = "Stop"
$Components = @($Components | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
[System.Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::InvariantCulture
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
New-Item -ItemType Directory -Force $Out | Out-Null
$deps = @((Get-Content "jxparallel-examples-native\target\classpath.txt" -Raw).Trim().Split(';') |
          Where-Object { (Split-Path $_ -Leaf) -notlike 'jxparallel-*' })
$cp = (@("jxparallel-examples-native\target\classes", "jxparallel-ui\target\classes", "jxparallel-forge\target\classes",
         "jxparallel-core\target\classes") + $deps) -join ';'

$columns = "component,n,rounds,c_build_us,c_reconcile_us,c_layout_us,c_total_us,l_refresh_us,l_layout_us,l_total_us,c_alloc_bytes,l_alloc_bytes,fallbacks".Split(',')
$rows = @()
foreach ($component in $Components) {
    for ($run = 1; $run -le $Runs; $run++) {
        $lines = & $Java -Xms512m -Xmx512m -cp $cp com.jxparallel.examples.nativeui.LiveSteadyBench $component $Rounds
        $line = @($lines | Where-Object { $_ -like 'STEADY *' })[0]
        if (-not $line) { throw "$component run ${run}: no STEADY line: $lines" }
        $values = $line.Substring(7).Split(',')
        $row = [ordered]@{ run = $run }
        for ($i = 0; $i -lt $columns.Count; $i++) { $row[$columns[$i]] = $values[$i] }
        $rows += [pscustomobject]$row
        Write-Host ("{0} run {1}: compiled {2} us, live {3} us" -f $component, $run, $row.c_total_us, $row.l_total_us)
    }
}
$rows | Export-Csv -Path (Join-Path $Out "steady-runs.csv") -NoTypeInformation -Encoding UTF8
function Get-Median([double[]]$v) {
    $s = @($v | Sort-Object)
    if ($s.Count % 2) { $s[($s.Count - 1) / 2] } else { ($s[$s.Count / 2 - 1] + $s[$s.Count / 2]) / 2 }
}
$median = foreach ($group in ($rows | Group-Object component)) {
    $line = [ordered]@{ component = $group.Name; n = $group.Group[0].n; runs = $group.Count }
    foreach ($c in $columns | Where-Object { $_ -notin @("component", "n", "rounds") }) {
        $line[$c] = Get-Median ([double[]]@($group.Group | ForEach-Object { [double]$_.$c }))
    }
    [pscustomobject]$line
}
$median | Export-Csv -Path (Join-Path $Out "steady-median.csv") -NoTypeInformation -Encoding UTF8
Write-Host "Saved steady-runs.csv and steady-median.csv in $Out"
