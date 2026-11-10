package com.jxparallel.forge.gradle;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

import com.jxparallel.forge.JXForge;
import com.jxparallel.forge.JxmlException;

/**
 * Compiles every {@code .jxml} under {@code sourceDirectory} (and under {@code fxmlJxmlDirectory}, when set) into a
 * Java class in {@code outputDirectory}. The Maven goal {@code generate}, as a cacheable task: the generated folder is
 * rewritten whole when any {@code .jxml} changes, so a deleted screen leaves no stale class behind. A JXML error
 * fails the build with its file and line.
 */
@CacheableTask
public abstract class JxforgeGenerateTask extends DefaultTask {
    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    /** Java package of the generated classes. */
    @Input
    @Optional
    public abstract Property<String> getPackageName();

    @Input
    public abstract Property<String> getClassSuffix();

    @Input
    public abstract Property<Boolean> getHoistConstants();

    /**
     * The compiler: the JXForge version and a hash of its classes. An input, so that a new JXForge (a changed schema
     * default, say) runs the task again and never keeps classes the old compiler generated, or takes them from the
     * build cache.
     */
    @Input
    public String getCompilerFingerprint() {
        return JXForge.compilerFingerprint();
    }

    @Internal
    public abstract DirectoryProperty getSourceDirectory();

    /** The JXML converted from FXML by {@code jxforgeFxml}; compiled too when {@code includeFxml} is true and it exists. */
    @Internal
    public abstract DirectoryProperty getFxmlJxmlDirectory();

    /** Whether the converted FXML is compiled too ({@code jxforge.fxml.enabled}). */
    @Input
    public abstract Property<Boolean> getIncludeFxml();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Every {@code .jxml} that is compiled, by path relative to its root folder (an {@code Include} depends on its
     * relative place). The task is skipped (NO-SOURCE) when there is none.
     */
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getSources() {
        ConfigurableFileCollection sources = getObjects().fileCollection();
        sources.from(getSourceDirectory().map(d -> d.getAsFileTree().matching(p -> p.include("**/*.jxml"))));
        if (getIncludeFxml().getOrElse(false)) {
            sources.from(getFxmlJxmlDirectory().map(d -> d.getAsFileTree().matching(p -> p.include("**/*.jxml"))));
        }
        return sources;
    }

    @TaskAction
    public void generate() {
        if (!getPackageName().isPresent() || getPackageName().get().trim().isEmpty()) {
            throw new InvalidUserDataException("JXForge: packageName is not set; add jxforge { packageName = \"com.acme.views\" }");
        }
        String packageName = getPackageName().get();
        File out = getOutputDirectory().get().getAsFile();
        // The folder belongs to this task alone. Starting from empty makes the result depend on the inputs only
        // (JXForge.compileDirectory skips a class newer than its .jxml, which a restored cache entry would fake).
        getFileSystemOperations().delete(spec -> spec.delete(out));
        if (!out.mkdirs() && !out.isDirectory()) {
            throw new GradleException("JXForge could not create " + out);
        }
        try {
            File[] roots = {
                    getSourceDirectory().getAsFile().getOrNull(),
                    getIncludeFxml().getOrElse(false) ? getFxmlJxmlDirectory().getAsFile().getOrNull() : null };
            for (File root : roots) {
                if (root == null || !root.isDirectory()) {
                    continue;
                }
                JXForge.Result result = JXForge.compileDirectory(root, out, packageName, getClassSuffix().get(),
                        getHoistConstants().get());
                getLogger().info(String.format("JXForge: %d written, %d up to date, %.1f ms (%s, from %s)",
                        result.written.size(), result.upToDate.size(), result.nanos / 1e6, packageName, root));
            }
        } catch (JxmlException e) {
            throw new GradleException(e.getMessage(), e);
        } catch (IOException e) {
            throw new GradleException("JXForge could not write " + out + ": " + e.getMessage(), e);
        }
    }
}
