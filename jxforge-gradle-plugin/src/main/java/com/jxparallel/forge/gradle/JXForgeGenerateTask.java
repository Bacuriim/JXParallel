package com.jxparallel.forge.gradle;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
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
        if (!src.isDirectory()) {
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
            Method m = forge.getMethod("compileDirectory", File.class, File.class, String.class, String.class,
                    boolean.class);
            Object result = m.invoke(null, src, out, getPackageName().get(), getClassSuffix().get(),
                    getHoistConstants().get());
            int written = ((Collection<?>) result.getClass().getField("written").get(result)).size();
            int upToDate = ((Collection<?>) result.getClass().getField("upToDate").get(result)).size();
            long nanos = result.getClass().getField("nanos").getLong(result);
            getLogger().lifecycle(String.format("JXForge: %d written, %d up to date, %.1f ms (%s)", written, upToDate,
                    nanos / 1e6, getPackageName().get()));
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

    /** A loader that sees the JDK and nothing of Gradle, so the forge and Gradle never mix classes. */
    private static ClassLoader platformLoader() {
        try {
            return (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
        } catch (ReflectiveOperationException e) {
            return null; // Java 8: bootstrap loader holds the JDK
        }
    }
}
