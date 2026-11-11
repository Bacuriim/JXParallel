# Runs JX Orders (jxparallel-examples-native, com.jxparallel.examples.orders.OrdersApp) from the reactor.
#   scripts\run-orders.ps1                                   the app, GPU renderer
#   scripts\run-orders.ps1 -Software                         the software renderer (-Djx.renderer=software)
#   scripts\run-orders.ps1 --screen=dashboard --theme=dark   the application's own options
# It is a shortcut for: mvn -pl jxparallel-examples-native -am -Prun compile   (add -o with -Offline)
# Needs JDK 8 or later and Maven on the PATH.
param(
    [switch]$Software,
    [switch]$Offline,
    [Parameter(ValueFromRemainingArguments = $true)] [string[]]$AppArgs
)
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $mvn = @("-q", "-pl", "jxparallel-examples-native", "-am", "-Prun", "-Dapp=orders")
    if ($Offline) { $mvn = @("-o") + $mvn }
    if ($Software) { $mvn += "-Dapp.jvm=-Djx.renderer=software" }
    if ($AppArgs) { $mvn += ("-Dapp.args=" + ($AppArgs -join " ")) }
    $mvn += "compile"
    & mvn @mvn
} finally {
    Pop-Location
}
