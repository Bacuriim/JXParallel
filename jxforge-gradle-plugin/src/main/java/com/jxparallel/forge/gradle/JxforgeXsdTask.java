package com.jxparallel.forge.gradle;

import java.io.File;
import java.io.IOException;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.jxparallel.forge.JxmlXsd;

/**
 * Writes the XML Schema of JXML ({@code jxml.xsd}), generated from the tag table of the JXForge version the plugin
 * carries, so an editor can complete and check {@code .jxml} files. Like the Maven goal {@code xsd} it is not part of
 * the build: run {@code gradle jxforgeXsd}. The schema depends on the plugin version only (which Gradle tracks as the
 * implementation of the task), so it is up to date until the plugin changes.
 */
@CacheableTask
public abstract class JxforgeXsdTask extends DefaultTask {
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void write() {
        File file = getOutputFile().get().getAsFile();
        try {
            JxmlXsd.write(file);
        } catch (IOException e) {
            throw new GradleException("JXForge could not write " + file + ": " + e.getMessage(), e);
        }
        getLogger().lifecycle("JXForge: JXML schema written to " + file);
    }
}
