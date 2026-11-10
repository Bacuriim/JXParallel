package com.jxparallel.forge.gradle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.PropertyResourceBundle;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

import com.jxparallel.forge.FxmlConversionException;
import com.jxparallel.forge.FxmlReport;
import com.jxparallel.forge.FxmlToJxml;
import com.jxparallel.forge.JxmlException;

/**
 * Converts every {@code .fxml} under {@code sourceDirectory} into a {@code .jxml} under {@code outputDirectory}, which
 * {@code jxforgeGenerate} then compiles. What could not be converted is written to {@code reportFile} and logged.
 * The Maven goal {@code fxml}, as a cacheable task.
 */
@CacheableTask
public abstract class JxforgeFxmlTask extends DefaultTask {
    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Internal
    public abstract DirectoryProperty getSourceDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public abstract Property<Boolean> getStrict();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getBundle();

    @Input
    public abstract MapProperty<String, String> getItemTypes();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    /** The {@code .fxml} files, by path relative to {@code sourceDirectory}; the task is skipped when there is none. */
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getSources() {
        ConfigurableFileCollection sources = getObjects().fileCollection();
        sources.from(getSourceDirectory().map(d -> d.getAsFileTree().matching(p -> p.include("**/*.fxml"))));
        return sources;
    }

    @TaskAction
    public void convert() {
        File source = getSourceDirectory().get().getAsFile();
        File out = getOutputDirectory().get().getAsFile();
        File reportFile = getReportFile().get().getAsFile();
        getFileSystemOperations().delete(spec -> spec.delete(out));
        FxmlToJxml.Options options = new FxmlToJxml.Options().strict(getStrict().get());
        try {
            if (getBundle().isPresent()) {
                InputStream in = new FileInputStream(getBundle().get().getAsFile());
                try {
                    options.bundle(new PropertyResourceBundle(new InputStreamReader(in, StandardCharsets.UTF_8)));
                } finally {
                    in.close();
                }
            }
            for (Map.Entry<String, String> type : getItemTypes().get().entrySet()) {
                options.itemType(type.getKey(), type.getValue());
            }
            FxmlReport report;
            try {
                report = FxmlToJxml.convertDirectory(source, out, options);
            } catch (FxmlConversionException e) {
                write(reportFile, e.getReport());
                throw new GradleException(e.getMessage(), e);
            }
            write(reportFile, report);
            getLogger().lifecycle(String.format("JXForge: FXML converted into %s: %d not converted, %d approximate, %d notes (%s)",
                    out, report.entries(FxmlReport.Level.UNSUPPORTED).size(),
                    report.entries(FxmlReport.Level.APPROXIMATE).size(), report.entries(FxmlReport.Level.NOTE).size(), reportFile));
            for (FxmlReport.Entry entry : report.entries()) {
                if (entry.level == FxmlReport.Level.UNSUPPORTED) {
                    getLogger().warn(entry.toString());
                }
            }
        } catch (JxmlException e) {
            throw new GradleException(e.getMessage(), e);
        } catch (IOException e) {
            throw new GradleException("JXForge could not convert FXML: " + e.getMessage(), e);
        }
    }

    private static void write(File file, FxmlReport report) throws IOException {
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        Writer writer = new OutputStreamWriter(new java.io.FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            writer.write(report.toText());
        } finally {
            writer.close();
        }
    }
}
