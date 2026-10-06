"""Fecho das dependências do Compose Desktop para o Maven: segue os POMs dos artefatos JVM/desktop e
troca cada artefato multiplataforma "raiz" pela variante JVM, como o Gradle faz com os metadados
de variante. Grava deps.txt (group:artifact:version por linha), que o build usa sem transitivas.
Uso: python closure.py"""
import os
import re
import subprocess

M2 = os.path.join(os.path.expanduser("~"), ".m2", "repository")
ROOTS = ["org.jetbrains.compose.desktop:desktop-jvm-windows-x64:1.7.1",
         "org.jetbrains.compose.material:material-desktop:1.7.1",
         "org.jetbrains.kotlin:kotlin-stdlib:2.0.21"]
# variantes JVM conhecidas dos artefatos multiplataforma (sufixo tentado em ordem)
SUFFIXES = ["-desktop", "-jvm", ""]
SKIP = re.compile(r"(-android|-ios|-js|-wasm|-macos|-linux|-uikit)")


def pom_path(g, a, v):
    return os.path.join(M2, *g.split("."), a, v, "%s-%s.pom" % (a, v))


REPOS = ["https://repo.maven.apache.org/maven2", "https://maven.google.com"]
MISSING = set()


def fetch(g, a, v, ext="pom"):
    """Baixa para o ~/.m2 (mesmo layout do Maven) direto por HTTP: uma chamada ao Maven por POM era lenta."""
    import urllib.request
    path = os.path.join(M2, *g.split("."), a, v, "%s-%s.%s" % (a, v, ext))
    if os.path.exists(path):
        return path
    if (g, a, v, ext) in MISSING:
        return None
    for repo in REPOS:
        url = "%s/%s/%s/%s/%s-%s.%s" % (repo, g.replace(".", "/"), a, v, a, v, ext)
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Apache-Maven/3.9 (Java 17; Windows)"})
            data = urllib.request.urlopen(req, timeout=60).read()
        except Exception:
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        open(path, "wb").write(data)
        return path
    MISSING.add((g, a, v, ext))
    return None


def deps(path):
    s = open(path, encoding="utf-8").read()
    out = []
    for block in re.findall(r"<dependency>(.*?)</dependency>", s, re.S):
        g = re.search(r"<groupId>([^<]+)", block).group(1)
        a = re.search(r"<artifactId>([^<]+)", block).group(1)
        v = re.search(r"<version>\[?([^<,\]]+)", block)
        scope = re.search(r"<scope>([^<]+)", block)
        if scope and scope.group(1) in ("test", "provided"):
            continue
        if v:
            out.append((g, a, v.group(1)))
    return out


def has_jar(g, a, v):
    s = open(pom_path(g, a, v), encoding="utf-8").read()
    pk = re.search(r"<packaging>([^<]+)", s)
    return not pk or pk.group(1) in ("jar", "bundle")


def jvm_variant(g, a, v):
    if SKIP.search(a):
        return None
    for suffix in SUFFIXES:
        if a.endswith(suffix) and suffix:
            candidates = [a]
            break
    else:
        candidates = [a + s for s in SUFFIXES]
    for c in candidates:
        if fetch(g, c, v):
            return c
    return None


if __name__ != "__main__":
    raise ImportError  # only fetch() is shared (classpath.py imports it before this point)

chosen = {}
todo = [tuple(r.split(":")) for r in ROOTS]
while todo:
    g, a, v = todo.pop()
    a2 = jvm_variant(g, a, v)
    if a2 is None:
        print("sem variante JVM:", g, a, v)
        continue
    key = (g, a2)
    if key in chosen and chosen[key] >= v:
        continue
    chosen[key] = max(v, chosen.get(key, v))
    todo.extend(deps(pom_path(g, a2, v)))

jars = []
with open("deps.txt", "w") as f:
    for (g, a), v in sorted(chosen.items()):
        if has_jar(g, a, v) and not a.endswith("-android"):
            f.write("%s:%s:%s\n" % (g, a, v))
            jar = fetch(g, a, v, "jar")
            if jar:
                jars.append(jar)
            else:
                print("sem jar:", g, a, v)
open("cp.txt", "w").write(";".join(jars))
print(len(chosen), "artefatos,", len(jars), "jars")
