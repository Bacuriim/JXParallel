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

Gradle 7.5 or newer, run on a JDK between 8 and 18 for Gradle 7.5 (JDK 11 works). The plugin targets Java 8.
It resolves `jxparallel-forge` and `jxparallel-core` from `mavenLocal()`, so install them first
(`mvn install` in the parent project).

```
gradle test
gradle publishToMavenLocal
```

The tests (TestKit, `withPluginClasspath()`) create temporary projects and run `compileJava` offline.
