<#
Builds the sample APK (JxHelloActivity) without the Android Gradle plugin: javac, D8, aapt2, zipalign, apksigner.

  powershell -File jxparallel-android\build-apk.ps1 [-SkipMaven] [-Install]

Needs: the Android SDK (platform android-34, build-tools 34.0.0), a JDK 17 (JAVA_HOME or -JavaHome) and, unless
-SkipMaven, Maven with the repository cached (it compiles jxparallel-core, -ui and -forge offline; no `mvn install`).
Output: jxparallel-android\build\jxhello.apk (signed with a generated debug key).
#>
param(
    [string]$AndroidHome = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }),
    [string]$Platform = 'android-34',
    [string]$BuildTools = '34.0.0',
    [string]$JavaHome = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Java\jdk-17' }),
    [switch]$SkipMaven,
    [switch]$Install
)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent $here
$build = Join-Path $here 'build'
$androidJar = Join-Path $AndroidHome "platforms\$Platform\android.jar"
$bt = Join-Path $AndroidHome "build-tools\$BuildTools"
$javac = Join-Path $JavaHome 'bin\javac.exe'
$java = Join-Path $JavaHome 'bin\java.exe'
$keytool = Join-Path $JavaHome 'bin\keytool.exe'
foreach ($required in @($androidJar, (Join-Path $bt 'aapt2.exe'), (Join-Path $bt 'd8.bat'), $javac)) {
    if (-not (Test-Path $required)) { throw "missing: $required" }
}
$env:JAVA_HOME = $JavaHome   # d8.bat and apksigner.bat start the JVM from here

function Run([string]$exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "failed ($LASTEXITCODE): $exe $($arguments -join ' ')" }
}

# 1. the three modules the sample needs, compiled by Maven offline into their target/classes
$core = Join-Path $repo 'jxparallel-core\target\classes'
$ui = Join-Path $repo 'jxparallel-ui\target\classes'
$forge = Join-Path $repo 'jxparallel-forge\target\classes'
if (-not $SkipMaven) {
    $env:MAVEN_OPTS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT'
    Push-Location $repo
    try { Run 'mvn' @('-o', '-q', '-pl', 'jxparallel-core,jxparallel-ui,jxparallel-forge', 'compile') } finally { Pop-Location }
}

if (Test-Path $build) { Remove-Item -Recurse -Force $build }
New-Item -ItemType Directory -Force $build | Out-Null

# 2. hello.jxml -> HelloView.java (the JXML compiler is plain Java 8, it runs on the desktop JVM)
$gen = Join-Path $build 'gen'
Run $java @('-cp', "$core;$ui;$forge", 'com.jxparallel.forge.JXForge', '--src', (Join-Path $here 'src\demo\jxml'),
    '--out', $gen, '--package', 'com.jxparallel.android.demo.gen', '--l10n', (Join-Path $build 'no-l10n'))

# 3. the Android classes: backend + sample + generated view, Java 8 bytecode against android.jar
$sources = @(Get-ChildItem -Recurse -Filter *.java (Join-Path $here 'src\main\java'), (Join-Path $here 'src\demo\java'), $gen |
    ForEach-Object { $_.FullName })
$list = Join-Path $build 'sources.txt'
[IO.File]::WriteAllLines($list, [string[]]($sources | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' }), (New-Object Text.UTF8Encoding($false)))
$classes = Join-Path $build 'classes'
New-Item -ItemType Directory -Force $classes | Out-Null
Run $javac @('--release', '8', '-nowarn', '-encoding', 'UTF-8', '-cp', "$androidJar;$core;$ui;$forge", '-d', $classes, "@$list")

# 4. jxparallel-ui without the desktop renderer: GLFW window, Skia/NanoVG/GDI painters, FreeType/HarfBuzz classes
#    (they import LWJGL and Skija, which do not exist on Android; nothing on the layout/paint path refers to them)
$uiAndroid = Join-Path $build 'ui-classes'
& robocopy $ui $uiAndroid /E /NFL /NDL /NJH /NJS /NP /XF 'JXWindow*.class' 'JXDisplay*.class' 'JXGdiSurface*.class' 'JXNativeCall*.class' `
    'JXFileDialogs*.class' 'JXNanoVG*.class' 'JXSkia*.class' 'JXSoftware*.class' 'JXHarfBuzz*.class' 'JXGlyphs*.class' | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy failed ($LASTEXITCODE)" }

# 5. D8: Java bytecode to classes.dex (the desugaring of lambdas is done here); warnings are printed on purpose
$dex = Join-Path $build 'dex'
New-Item -ItemType Directory -Force $dex | Out-Null
$jars = @()
$inputs = [ordered]@{ app = $classes; ui = $uiAndroid; core = $core; forge = $forge }
foreach ($name in $inputs.Keys) {   # this D8 takes jars and class files, not directories
    $jarFile = Join-Path $build "$name.jar"
    Run (Join-Path $JavaHome 'bin\jar.exe') @('--create', '--file', $jarFile, '-C', $inputs[$name], '.')
    $jars += $jarFile
}
Run (Join-Path $bt 'd8.bat') (@('--release', '--min-api', '26', '--lib', $androidJar, '--output', $dex) + $jars)

# 6. manifest + dex into an unsigned APK
$unsigned = Join-Path $build 'unsigned.apk'
Run (Join-Path $bt 'aapt2.exe') @('link', '-o', $unsigned, '-I', $androidJar, '--manifest', (Join-Path $here 'src\demo\AndroidManifest.xml'),
    '--min-sdk-version', '26', '--target-sdk-version', '34')
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open($unsigned, 'Update')
try {
    [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $dex 'classes.dex'), 'classes.dex', [IO.Compression.CompressionLevel]::Optimal)
} finally { $zip.Dispose() }

# 7. align, then sign with a debug key made here (the standard Android debug key parameters; not for release)
$aligned = Join-Path $build 'aligned.apk'
Run (Join-Path $bt 'zipalign.exe') @('-f', '-p', '4', $unsigned, $aligned)
$keystore = Join-Path $build 'debug.keystore'
Run $keytool @('-genkeypair', '-keystore', $keystore, '-storepass', 'android', '-keypass', 'android', '-alias', 'androiddebugkey',
    '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Android Debug,O=Android,C=US')
$apk = Join-Path $build 'jxhello.apk'
Run (Join-Path $bt 'apksigner.bat') @('sign', '--ks', $keystore, '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', $apk, $aligned)
Run (Join-Path $bt 'apksigner.bat') @('verify', $apk)
Run (Join-Path $bt 'aapt2.exe') @('dump', 'badging', $apk) | Select-Object -First 6
Write-Host "APK: $apk ($([math]::Round((Get-Item $apk).Length / 1KB)) KB)"

if ($Install) {
    $adb = Join-Path $AndroidHome 'platform-tools\adb.exe'
    Run $adb @('install', '-r', $apk)
    Run $adb @('shell', 'am', 'start', '-n', 'com.jxparallel.android.demo/.JxHelloActivity')
}
