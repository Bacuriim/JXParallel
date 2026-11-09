package com.jxparallel.forge.maven;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.jxparallel.forge.JxmlXsd;

/**
 * Writes the XML Schema of JXML ({@code jxml.xsd}), generated from the tag table of this version of JXForge, so an
 * editor can complete and check {@code .jxml} files. It is not bound to a phase: run {@code mvn jxforge:xsd}.
 */
@Mojo(name = "xsd", threadSafe = true)
public class XsdMojo extends AbstractMojo {
    /** Where the schema is written. */
    @Parameter(property = "jxforge.xsd", defaultValue = "${project.build.directory}/jxml.xsd")
    private File outputFile;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            JxmlXsd.write(outputFile);
        } catch (IOException e) {
            throw new MojoExecutionException("JXForge could not write " + outputFile, e);
        }
        getLog().info("JXForge: JXML schema written to " + outputFile);
    }
}
