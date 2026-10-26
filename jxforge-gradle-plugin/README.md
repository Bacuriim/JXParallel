# jxforge-gradle-plugin

Gradle plugin for JXForge: compiles `.jxml` files to Java at build time, the same job as
`jxforge-maven-plugin`. Plugin id `com.jxparallel.jxforge`.

## Usage

```groovy
plugins {
    id 'java'
    id 'com.jxparallel.jxforge'
}

repositories {
    mavenLocal()          // or wherever com.jxparallel artifacts are published
}

dependencies {
    // the generated classes use the forge runtime
    implementation 'com.jxparallel:jxparallel-forge:0.1.0-SNAPSHOT'
}

jxforge {
    packageName = 'com.example.ui'   // required
}
```

`jxforgeGenerate` runs before `compileJava`, and its output directory is added to the main source set.
The compiler itself (`jxparallel-forge`) is resolved from the project repositories through a detached
configuration when the task runs; it is not bundled in the plugin.

## Parameters (`jxforge { }`)

| Property           | Default                               | Meaning                                                |
|--------------------|---------------------------------------|--------------------------------------------------------|
| `packageName`      | none, required                        | Java package of the generated classes                  |
| `sourceDirectory`  | `src/main/jxml`                       | Where the `.jxml` files are; a missing directory generates nothing |
| `outputDirectory`  | `build/generated/sources/jxforge`     | Where the Java is written                              |
| `classSuffix`      | `View`                                | `Home.jxml` becomes `HomeView`                         |
| `hoistConstants`   | `true`                                | Build subtrees without bindings once and share them    |
| `forgeVersion`     | the plugin's version                  | Version of `jxparallel-forge` the task runs            |

The task is cacheable and up to date while the `.jxml` files, the options and the forge jar are unchanged.
A JXML error fails the build with the message `File.jxml:line: problem`.

## Build and test

Gradle 7.5 or newer, run on a JDK between 8 and 18 for Gradle 7.5 (JDK 11 and 17 work; Gradle 7.5.1 on
JDK 21 fails with `Unsupported class file major version 65`, and 8.5 is the first release that runs on 21).
The plugin targets Java 8.
It resolves `jxparallel-forge` and `jxparallel-core` from `mavenLocal()`, so install them first
(`mvn install` in the parent project).

```
gradle test
gradle publishToMavenLocal
```

The tests (TestKit, `withPluginClasspath()`) create temporary projects and run `compileJava` offline.

### `Unable to establish loopback connection` on JDK 17

On Windows, JDK 16 and newer open the pipe of every `Selector` over an AF_UNIX socket created in the temporary
directory (`TEMP`), and fall back to TCP loopback only when the bind fails. If the socket can be bound there but
not connected (`SocketException: Invalid argument: connect`), `Selector.open()` throws
`IOException: Unable to establish loopback connection`, and Gradle fails at once, in the client, the daemon and
the test workers. It is not a Gradle or plugin problem: a one-class Java program that calls `Selector.open()`
fails the same way on JDK 17 and 21 and works on JDK 11 (which uses TCP). It was seen when `TEMP` was inside
the user profile (`C:\Users\<name>\AppData\Local\Temp`) and fixed by pointing `TEMP` and `TMP` to another
folder, for example `C:\dev\tmp`; the same program works with a `TEMP` of `C:\Windows\Temp`.

```
set TEMP=C:\dev\tmp
set TMP=C:\dev\tmp
set JAVA_HOME=C:\Program Files\Java\jdk-17
gradle test
```

The daemon, the test workers and the TestKit daemons inherit the variables, so nothing else is needed;
`-Djdk.net.unixdomain.tmpdir` in `GRADLE_OPTS` fixes only the Gradle client. The folder must exist.
