#!/usr/bin/env bash
# Compila o ComposeBench (o aplicativo do FxBackendBench em Compose Desktop) sem Gradle.
#   1. classpath.py baixa os artefatos JVM/desktop do Compose 1.7.1 (Maven Central e Maven do Google)
#      e grava cp.txt;
#   2. o Maven resolve o compilador Kotlin 2.0.21 e o plugin do Compose (compiler-pom.xml);
#   3. o compilador roda direto (K2JVMCompiler) com -Xplugin do Compose.
# Requer JDK 17 (64 bits). Saída: out/ e cp.txt; o measure-fx-backends.ps1 recebe
# -ComposeClasspath "out;<conteúdo de cp.txt>".
set -e
cd "$(dirname "$0")"
J17="${JAVA17_HOME:-/c/Program Files/Java/jdk-17}"
python classpath.py
mkdir -p compiler && cp compiler-pom.xml compiler/pom.xml
(cd compiler && JAVA_HOME="$J17" mvn -q dependency:build-classpath -Dmdep.outputFile=kcp.txt)
KCP=$(cat compiler/kcp.txt)
PLUGIN=$(tr ';' '\n' < compiler/kcp.txt | grep compose-compiler-plugin)
rm -rf out && mkdir out
"$J17/bin/java" -cp "$KCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -jvm-target 17 \
  "-Xplugin=$PLUGIN" -cp "$(cat cp.txt)" -d out ComposeBench.kt
