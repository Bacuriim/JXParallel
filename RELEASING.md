# Releasing to Maven Central

JXParallel is published to Maven Central through the Central Portal (central.sonatype.com) under the group
`io.github.bacuriim`. The Java packages stay `com.jxparallel`. A release is two deployments, because the modules
need two JDKs:

| Deployment | JDK | Modules |
|---|---|---|
| A | 8 with its bundled JavaFX (`jre/lib/ext/jfxrt.jar`) | `jxparallel-parent`, `-core`, `-ui`, `-junit4`, `-junit5`, `-mockito`, `-powermock`, `-fx`, `-fx-controlsfx` |
| B | 17, `-Plegacy-javafx` | `-javafx`, `-fxml` (built on OpenJFX 21) |

`jxparallel-fx` must be built on JDK 8: it is the API that Java 8 applications use. Examples, benchmarks and
jcstress are not published (`skipPublishing`). The `release` profile adds the sources and Javadoc jars, the GPG
signatures and the Central upload. The upload stops at "validated": publishing is done by hand in the Portal, and a
published version can never be changed or removed.

## Once

1. Sign in at https://central.sonatype.com with the GitHub account `Bacuriim`. The namespace
   `io.github.bacuriim` is verified from the GitHub login (Namespaces page).
2. Generate a user token (Account, Generate User Token) and put it in `~/.m2/settings.xml`:

   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>TOKEN-USERNAME</username>
         <password>TOKEN-PASSWORD</password>
       </server>
     </servers>
   </settings>
   ```

3. Create a GPG key and publish it to a key server that Central reads (Git for Windows ships `gpg` in
   `C:\Program Files\Git\usr\bin`):

   ```
   gpg --full-generate-key
   gpg --list-secret-keys --keyid-format long
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY-ID>
   ```

   The Maven GPG plugin asks for the passphrase through the GPG agent, or reads it from the environment variable
   `MAVEN_GPG_PASSPHRASE`.

## Each release

From a clean checkout of `main`, with the version in the poms set to the release (no `-SNAPSHOT`):

```
# deployment A
set JAVA_HOME=C:\Program Files\Java\jdk-1.8
mvn -P release clean deploy

# deployment B (core and ui come from the local repository, installed by A)
set JAVA_HOME=C:\Program Files\Java\jdk-17
mvn -P release,legacy-javafx -pl jxparallel-javafx,jxparallel-fxml clean deploy
```

Each command waits until Central has validated the upload. Then open https://central.sonatype.com/publishing,
check the two deployments, and press Publish on each. Tag the commit (`git tag v0.1.0`, `git push origin v0.1.0`)
and move `main` to the next `-SNAPSHOT`.

To check a release without sending anything, add `-DskipUpload=true` (and `-Dgpg.skip=true` without a key): the
bundle is written to `target/central-publishing/central-bundle.zip` of the last module.

## Native classifiers of `jxparallel-ui`

The natives are not separate deployments. The published `jxparallel-ui` pom keeps its OS-activated profiles
(`natives-windows-x64`, `natives-windows-x86`, `natives-linux-x64`, and since the macOS/ARM64 work
`natives-macos-x64`, `natives-macos-arm64`, `natives-linux-arm64`, `natives-windows-arm64`); a consumer's Maven
activates the one that matches its own OS and CPU and pulls the LWJGL `natives-*` jars (and Skija's platform jar
where one exists) from Central. Nothing is uploaded for them, and the profiles of the existing platforms are
unchanged, so what a Windows or Linux x64 user resolves does not change. The new profiles cannot activate there.

Before a release, check that every artifact the profiles name still exists on Central (they are fixed by
`lwjgl.version` and `skija.version` in the parent pom):

```
mvn -pl jxparallel-ui -am -P natives-macos-x64 dependency:resolve
mvn -pl jxparallel-ui -am -P natives-macos-arm64 dependency:resolve
mvn -pl jxparallel-ui -am -P natives-linux-arm64 dependency:resolve
mvn -pl jxparallel-ui -am -P natives-windows-arm64 dependency:resolve
```

Skija 0.116.4 publishes no `skija-windows-arm64` or `skija-linux-arm64` (those start at 0.143), so on those two
platforms the window uses NanoVG. State in the release notes which of the four new platforms CI has run green
(`.github/workflows/build.yml`); until a platform has, it is configured but unproven. Applications on macOS must
start the JVM with `-XstartOnFirstThread` (`docs/native-ui.md`).
