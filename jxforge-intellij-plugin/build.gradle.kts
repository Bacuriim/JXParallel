import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.9.0"
}

group = "io.github.bacuriim"
version = providers.gradleProperty("pluginVersion").get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // The oldest platform the plugin supports: 2024.2 (build 242). Code that compiles here also runs on newer ones.
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").getOrElse("2024.2.5"))
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
        pluginVerifier()
        zipSigner()
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    pluginConfiguration {
        id = "io.github.bacuriim.jxforge"
        name = "JXForge (JXML)"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "242"
            // no until-build: the plugin only uses stable platform APIs
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            // 2024.2 (the minimum) and the newest release; the unified IntelliJ IDEA replaced the Community edition in 2025.3
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2.5")
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2.4")
            ide(IntelliJPlatformType.IntellijIdea, providers.gradleProperty("latestIde").getOrElse("2026.3"))
        }
    }
}

// ---- jxml.xsd, generated from the JXForge sources of this repository -------------------------------------------------
// Build the forge first: mvn -o -q -pl jxparallel-forge -am package -DskipTests (JDK 17), see README.md.
val forgeJars = fileTree("${rootDir}/../jxparallel-forge/target") { include("jxparallel-forge-*.jar"); exclude("*-sources.jar", "*-javadoc.jar", "*-tests.jar") } +
        fileTree("${rootDir}/../jxparallel-core/target") { include("jxparallel-core-*.jar"); exclude("*-sources.jar", "*-javadoc.jar", "*-tests.jar") }

val generateJxmlXsd by tasks.registering(JavaExec::class) {
    description = "Runs com.jxparallel.forge.JxmlXsd to write jxml.xsd into the plugin resources."
    val out = layout.buildDirectory.file("generated/xsd/io/github/bacuriim/jxforge/jxml.xsd")
    inputs.files(forgeJars)
    outputs.file(out)
    classpath = forgeJars
    mainClass = "com.jxparallel.forge.JxmlXsd"
    args("--out", out.get().asFile.absolutePath)
    doFirst {
        if (forgeJars.files.size < 2) {
            throw GradleException("jxparallel-forge and jxparallel-core jars not found in ../*/target. Build them first: " +
                    "mvn -o -q -pl jxparallel-forge -am package -DskipTests")
        }
    }
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/xsd"))
}
tasks.processResources { dependsOn(generateJxmlXsd) }

tasks {
    test {
        useJUnit()
        jvmArgs("-Xmx1g")
    }
    wrapper {
        gradleVersion = "8.10.2"
    }
}
