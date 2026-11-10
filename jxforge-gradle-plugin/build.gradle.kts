plugins {
    `java-gradle-plugin`
    `maven-publish`
    // Adds publishPlugins (the Gradle Plugin Portal), sources and Javadoc jars, and the plugin marker publication.
    // publishPlugins needs gradle.publish.key / gradle.publish.secret; nothing is published without them.
    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "io.github.bacuriim"
// Keep equal to the Maven build of the repository: the plugin runs the jxparallel-forge of the same version.
version = providers.gradleProperty("jxparallelVersion").get()

repositories {
    // jxparallel-forge and jxparallel-core are built by the Maven build of this repository
    // (mvn -pl jxparallel-forge -am package -DskipTests). Until a forge release is on Maven Central they are
    // taken from there; afterwards Maven Central below resolves them and this folder is simply empty.
    flatDir {
        dirs("../jxparallel-forge/target", "../jxparallel-core/target")
    }
    mavenCentral()
}

val sample: Configuration by configurations.creating

dependencies {
    // Runs in the classloader of the plugin: the user's build script needs no classpath entry for it.
    implementation("io.github.bacuriim:jxparallel-forge:$version")
    implementation("io.github.bacuriim:jxparallel-core:$version")

    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")

    // What the sample projects of the tests compile the generated classes against.
    sample("io.github.bacuriim:jxparallel-forge:$version")
    sample("io.github.bacuriim:jxparallel-core:$version")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    if (JavaVersion.current().isJava9Compatible) {
        options.release.set(8)
    }
}

gradlePlugin {
    website.set("https://github.com/Bacuriim/JXParallel")
    vcsUrl.set("https://github.com/Bacuriim/JXParallel.git")
    plugins {
        create("jxforge") {
            id = "io.github.bacuriim.jxforge"
            implementationClass = "com.jxparallel.forge.gradle.JxforgePlugin"
            displayName = "JXForge"
            description = "Compiles JXML screens of the JXParallel native UI into plain Java at build time, " +
                "writes the JXML XML Schema and converts FXML to JXML."
            tags.set(listOf("jxparallel", "jxml", "ui", "code-generation", "fxml"))
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("JXForge Gradle Plugin")
            description.set("Compiles JXML screens into Java at build time (tasks jxforgeGenerate, jxforgeXsd, jxforgeFxml).")
            url.set("https://github.com/Bacuriim/JXParallel")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/license/mit")
                }
            }
            developers {
                developer {
                    name.set("Bacuriim")
                    url.set("https://github.com/Bacuriim")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/Bacuriim/JXParallel.git")
                developerConnection.set("scm:git:ssh://git@github.com/Bacuriim/JXParallel.git")
                url.set("https://github.com/Bacuriim/JXParallel")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // The Gradle version the TestKit builds run on: -PtestGradleVersion=7.5.1 (default: the wrapper's own).
    systemProperty("jxforge.testGradleVersion", providers.gradleProperty("testGradleVersion").getOrElse(""))
    val sampleFiles = sample
    doFirst {
        systemProperty("jxforge.sampleClasspath", sampleFiles.files.joinToString(File.pathSeparator) { it.absolutePath })
    }
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
