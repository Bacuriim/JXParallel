"""Classpath do Compose Desktop 1.7.1 (Windows x64) sem Gradle: os artefatos JVM/desktop que o Gradle
escolheria pelos metadados de variante, baixados do Maven Central e do Maven do Google para o
~/.m2. Grava cp.txt. Uso: python classpath.py"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import importlib.util  # noqa: E402
_spec = importlib.util.spec_from_file_location("closure", os.path.join(os.path.dirname(os.path.abspath(__file__)), "closure.py"))
_mod = importlib.util.module_from_spec(_spec)
try:
    _spec.loader.exec_module(_mod)
except ImportError:
    pass  # closure.py stops after defining fetch() when imported
fetch = _mod.fetch

C = "1.7.1"
ARTIFACTS = [
    "org.jetbrains.compose.runtime:runtime-desktop:" + C,
    "org.jetbrains.compose.runtime:runtime-saveable-desktop:" + C,
    "org.jetbrains.compose.ui:ui-desktop:" + C,
    "org.jetbrains.compose.ui:ui-geometry-desktop:" + C,
    "org.jetbrains.compose.ui:ui-graphics-desktop:" + C,
    "org.jetbrains.compose.ui:ui-text-desktop:" + C,
    "org.jetbrains.compose.ui:ui-unit-desktop:" + C,
    "org.jetbrains.compose.ui:ui-util-desktop:" + C,
    "org.jetbrains.compose.ui:ui-tooling-preview:" + C,
    "org.jetbrains.compose.foundation:foundation-desktop:" + C,
    "org.jetbrains.compose.foundation:foundation-layout-desktop:" + C,
    "org.jetbrains.compose.animation:animation-desktop:" + C,
    "org.jetbrains.compose.animation:animation-core-desktop:" + C,
    "org.jetbrains.compose.material:material-desktop:" + C,
    "org.jetbrains.compose.material:material-ripple-desktop:" + C,
    "org.jetbrains.compose.material:material-icons-core-desktop:" + C,
    "org.jetbrains.compose.desktop:desktop:" + C,
    "org.jetbrains.skiko:skiko-awt:0.8.18",
    "org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.8.18",
    "org.jetbrains.kotlin:kotlin-stdlib:2.0.21",
    "org.jetbrains:annotations:23.0.0",
    "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0",
    "org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0",
    "org.jetbrains.kotlinx:atomicfu-jvm:0.23.2",
    "androidx.annotation:annotation-jvm:1.8.0",
    "androidx.arch.core:core-common:2.2.0",
    "androidx.collection:collection-jvm:1.4.0",
    "androidx.lifecycle:lifecycle-common-jvm:2.8.5",
    "androidx.lifecycle:lifecycle-runtime-desktop:2.8.5",
    "androidx.lifecycle:lifecycle-viewmodel-desktop:2.8.5",
    "androidx.lifecycle:lifecycle-runtime-compose-desktop:2.8.5",
    "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-desktop:2.8.4",
]

jars = []
for coord in ARTIFACTS:
    g, a, v = coord.split(":")
    jar = fetch(g, a, v, "jar")
    if not jar:
        # o Python desta máquina recusa o certificado do Maven Central; o Maven (Java) aceita
        import subprocess
        subprocess.run(["mvn.cmd", "-q", "dependency:get", "-Dtransitive=false",
                        "-DremoteRepositories=google::default::https://maven.google.com",
                        "-Dartifact=" + coord], capture_output=True)
        path = os.path.join(os.path.expanduser("~"), ".m2", "repository", *g.split("."), a, v, "%s-%s.jar" % (a, v))
        jar = path if os.path.exists(path) else None
    if not jar:
        sys.exit("não baixou: " + coord)
    jars.append(jar)
open("cp.txt", "w").write(";".join(jars))
print(len(jars), "jars,", sum(os.path.getsize(j) for j in jars) // 1048576, "MB")
