package com.jxparallel.forge.gradle;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Calls {@code JXForge.compileDirectory} on the forge jar resolved for the build. A JXML error fails
 * the task with its file and line; a missing source directory generates nothing.
 */
@CacheableTask
public abstract class JXForgeGenerateTask extends DefaultTask {
    /** The directory itself; it may not exist. What is tracked as input is {@link #getSources()}. */
    @Internal
    public abstract DirectoryProperty getSourceDirectory();

    /** The files under the source directory (empty when it is missing). */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    /** The translation directory itself; it may not exist. What is tracked as input is {@link #getL10nFiles()}. */
    @Internal
    public abstract DirectoryProperty getL10nDirectory();

    /** The files under the translation directory (empty when it is missing). */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getL10nFiles();

    @Input
    public abstract Property<Boolean> getL10nStrict();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public abstract Property<String> getPackageName();

    @Input
    public abstract Property<String> getClassSuffix();

    @Input
    public abstract Property<Boolean> getHoistConstants();

    @Classpath
    public abstract ConfigurableFileCollection getForgeClasspath();

    @TaskAction
    public void generate() {
        File src = getSourceDirectory().get().getAsFile();
        File out = getOutputDirectory().get().getAsFile();
        if (!getPackageName().isPresent()) {
            throw new GradleException("jxforge.packageName is required");
        }
        // a source directory that is gone still has generated classes to remove when an earlier run left a manifest
        if (!src.isDirectory() && !new File(out, ".jxforge-manifest").isFile()) {
            getLogger().info("JXForge: no {}, nothing to do", src);
            return;
        }
        List<URL> urls = new ArrayList<>();
        try {
            for (File f : getForgeClasspath().getFiles()) {
                urls.add(f.toURI().toURL());
            }
        } catch (IOException e) {
            throw new GradleException("JXForge classpath is invalid", e);
        }
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), platformLoader())) {
            Class<?> forge = loader.loadClass("com.jxparallel.forge.JXForge");
            File l10n = getL10nDirectory().isPresent() ? getL10nDirectory().get().getAsFile() : null;
            Object result;
            try {
                // the translations came with a later forge: with an older jar the five-argument method is all there is
                Method m = forge.getMethod("compileDirectory", File.class, File.class, String.class, String.class,
                        boolean.class, File.class, boolean.class);
                result = m.invoke(null, src, out, getPackageName().get(), getClassSuffix().get(), getHoistConstants().get(),
                        l10n, getL10nStrict().getOrElse(false));
            } catch (NoSuchMethodException old) {
                if (l10n != null && l10n.isDirectory()) {
                    getLogger().warn("JXForge: {} is ignored, this version of the forge has no translations", l10n);
                }
                Method m = forge.getMethod("compileDirectory", File.class, File.class, String.class, String.class,
                        boolean.class);
                result = m.invoke(null, src, out, getPackageName().get(), getClassSuffix().get(), getHoistConstants().get());
            }
            // the result of an older forge may lack warnings and removed: whatever is there is logged, the rest is skipped
            for (Object warning : collection(result, "warnings")) {
                getLogger().warn("{}", warning);
            }
            Collection<?> removed = collection(result, "removed");
            for (Object file : removed) {
                getLogger().lifecycle("JXForge: removed {}", relative(out, file));
            }
            int written = collection(result, "written").size();
            int upToDate = collection(result, "upToDate").size();
            long nanos = number(result, "nanos");
            String counts = hasField(result, "removed")
                    ? String.format("%d written, %d up to date, %d removed", written, upToDate, removed.size())
                    : String.format("%d written, %d up to date", written, upToDate);
            getLogger().lifecycle(String.format("JXForge: %s, %.1f ms (%s)", counts, nanos / 1e6, getPackageName().get()));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw new GradleException("JXForge could not write " + out, cause);
            }
            // JxmlException: its message already carries file:line.
            throw new GradleException(cause.getMessage(), cause);
        } catch (ReflectiveOperationException | IOException e) {
            throw new GradleException("JXForge could not run: " + e, e);
        }
    }

    private static boolean hasField(Object result, String name) {
        try {
            result.getClass().getField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    /** The collection in the public field {@code name} of the result; empty when this forge has no such field. */
    private static Collection<?> collection(Object result, String name) throws IllegalAccessException {
        try {
            Object value = result.getClass().getField(name).get(result);
            return value instanceof Collection ? (Collection<?>) value : Collections.emptyList();
        } catch (NoSuchFieldException e) {
            return Collections.emptyList();
        }
    }

    private static long number(Object result, String name) throws IllegalAccessException {
        try {
            return result.getClass().getField(name).getLong(result);
        } catch (NoSuchFieldException e) {
            return 0;
        }
    }

    /** The path of a removed file below the output directory, with {@code /}; the whole path when it is somewhere else. */
    private static String relative(File out, Object file) {
        String base = out.getAbsolutePath();
        String path = ((File) file).getAbsolutePath();
        if (path.startsWith(base + File.separator)) {
            path = path.substring(base.length() + 1);
        }
        return path.replace(File.separatorChar, '/');
    }

    /** A loader that sees the JDK and nothing of Gradle, so the forge and Gradle never mix classes. */
    private static ClassLoader platformLoader() {
        try {
            return (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
        } catch (ReflectiveOperationException e) {
            return null; // Java 8: bootstrap loader holds the JDK
        }
    }
}
