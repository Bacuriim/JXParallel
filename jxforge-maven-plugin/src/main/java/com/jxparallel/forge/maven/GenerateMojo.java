package com.jxparallel.forge.maven;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.jxparallel.forge.JXForge;
import com.jxparallel.forge.JxmlException;

/**
 * Compiles every {@code .jxml} under {@code sourceDirectory} into a Java class in
 * {@code outputDirectory} and adds that directory to the compile sources. Unchanged files are
 * skipped. A JXML error fails the build with its file and line.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class GenerateMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.basedir}/src/main/jxml")
    private File sourceDirectory;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/jxforge")
    private File outputDirectory;

    /** Java package of the generated classes. */
    @Parameter(required = true)
    private String packageName;

    @Parameter(defaultValue = "View")
    private String classSuffix;

    /** Build subtrees without bindings once, when the class loads, and share them. */
    @Parameter(defaultValue = "true")
    private boolean hoistConstants;

    /**
     * The translation files: ARB ({@code app_en.arb}, {@code app_pt_BR.arb}) and/or Android {@code values-xx/strings.xml}.
     * Screens write {@code text="%{key}"}; a typed {@code Strings} class is generated next to them. A missing directory
     * means no translations.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/l10n")
    private File l10nDirectory;

    /** A missing or extra translation in a locale other than the default fails the build instead of being a warning. */
    @Parameter(defaultValue = "false")
    private boolean l10nStrict;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!sourceDirectory.isDirectory()) {
            getLog().info("JXForge: no " + sourceDirectory + ", nothing to do");
            return;
        }
        try {
            JXForge.Result result = JXForge.compileDirectory(sourceDirectory, outputDirectory, packageName,
                    classSuffix, hoistConstants, l10nDirectory, l10nStrict);
            for (String warning : result.warnings) {
                getLog().warn(warning);
            }
            getLog().info(String.format("JXForge: %d written, %d up to date, %d removed, %.1f ms (%s)", result.written.size(),
                    result.upToDate.size(), result.removed.size(), result.nanos / 1e6, packageName));
        } catch (JxmlException e) {
            throw new MojoFailureException(e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("JXForge could not write " + outputDirectory, e);
        }
        project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
    }
}
