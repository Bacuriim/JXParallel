# Runs JX Orders (jxparallel-examples-native, com.jxparallel.examples.orders.OrdersApp) from the reactor's classes.
#   scripts\run-orders.ps1                                   the app, GPU renderer
#   scripts\run-orders.ps1 -Software                         the software renderer (-Djx.renderer=software)
#   scripts\run-orders.ps1 --screen=dashboard --theme=dark   the application's own options
# Needs JDK 8 or later and Maven on the PATH; builds offline when it can (-Offline).
param(
    [switch]$Software,
    [switch]$Offline,
    [Parameter(ValueFromRemainingArguments = $true)] [string[]]$AppArgs
)
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $mvn = @("-q", "-pl", "jxparallel-examples-native", "-am", "compile", "dependency:build-classpath",
             "-Dmdep.outputFile=target/orders-classpath.txt", "-Dmdep.includeScope=runtime")
    if ($Offline) { $mvn = @("-o") + $mvn }
    & mvn @mvn
    if ($LASTEXITCODE -ne 0) { throw "the build failed" }
    $cp = (Get-Content (Join-Path $root "jxparallel-examples-native\target\orders-classpath.txt") -Raw).Trim()
    $classes = Join-Path $root "jxparallel-examples-native\target\classes"
    $jvm = @()
    if ($Software) { $jvm += "-Djx.renderer=software" }
    & java @jvm -cp "$classes;$cp" com.jxparallel.examples.orders.OrdersApp @AppArgs
} finally {
    Pop-Location
}
