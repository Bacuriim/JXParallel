package com.jxparallel.forge.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.PropertyResourceBundle;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.jxparallel.forge.FxmlConversionException;
import com.jxparallel.forge.FxmlReport;
import com.jxparallel.forge.FxmlToJxml;
import com.jxparallel.forge.JxmlException;

/**
 * Converts every {@code .fxml} under {@code sourceDirectory} into a {@code .jxml} under {@code outputDirectory}, which
 * the {@code generate} goal then compiles (it reads {@code fxmlJxmlDirectory}, the same folder). Put this execution
 * before {@code generate} in the POM. What could not be converted is written to {@code reportFile} and logged.
 */
@Mojo(name = "fxml", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class FxmlMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.basedir}/src/main/resources")
    private File sourceDirectory;

    @Parameter(defaultValue = "${project.build.directory}/generated-jxml")
    private File outputDirectory;

    /** Fail the build when anything is not converted (notes such as fx:id do not fail it). */
    @Parameter(property = "jxforge.fxml.strict", defaultValue = "false")
    private boolean strict;

    /** A {@code .properties} file that resolves the {@code %key} values of the FXML. */
    @Parameter
    private File bundle;

    /** The class of the rows of each table or list, by fx:id ({@code *} for all): {@code <orders>com.acme.Order</orders>}. */
    @Parameter
    private Map<String, String> itemTypes;

    @Parameter(defaultValue = "${project.build.directory}/jxforge-fxml-report.txt")
    private File reportFile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!sourceDirectory.isDirectory()) {
            getLog().info("JXForge: no " + sourceDirectory + ", nothing to convert");
            return;
        }
        FxmlToJxml.Options options = new FxmlToJxml.Options().strict(strict);
        try {
            if (bundle != null) {
                InputStream in = new FileInputStream(bundle);
                try {
                    options.bundle(new PropertyResourceBundle(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)));
                } finally {
                    in.close();
                }
            }
            if (itemTypes != null) {
                for (Map.Entry<String, String> type : itemTypes.entrySet()) {
                    options.itemType(type.getKey(), type.getValue());
                }
            }
            FxmlReport report;
            try {
                report = FxmlToJxml.convertDirectory(sourceDirectory, outputDirectory, options);
            } catch (FxmlConversionException e) {
                write(e.getReport());
                throw new MojoFailureException(e.getMessage(), e);
            }
            write(report);
            getLog().info(String.format("JXForge: FXML converted into %s: %d not converted, %d approximate, %d notes (%s)",
                    outputDirectory, report.entries(FxmlReport.Level.UNSUPPORTED).size(),
                    report.entries(FxmlReport.Level.APPROXIMATE).size(), report.entries(FxmlReport.Level.NOTE).size(), reportFile));
            for (FxmlReport.Entry entry : report.entries()) {
                if (entry.level == FxmlReport.Level.UNSUPPORTED) {
                    getLog().warn(entry.toString());
                }
            }
        } catch (JxmlException e) {
            throw new MojoFailureException(e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("JXForge could not convert FXML: " + e.getMessage(), e);
        }
    }

    private void write(FxmlReport report) throws IOException {
        if (reportFile == null) {
            return;
        }
        if (reportFile.getParentFile() != null) {
            reportFile.getParentFile().mkdirs();
        }
        Writer writer = new OutputStreamWriter(new java.io.FileOutputStream(reportFile), StandardCharsets.UTF_8);
        try {
            writer.write(report.toText());
        } finally {
            writer.close();
        }
    }
}
