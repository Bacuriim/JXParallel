package com.jxparallel.forge.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/** The {@code jxforge { }} block. Only {@code packageName} has no default. */
public abstract class JXForgeExtension {
    /** Java package of the generated classes (required). */
    public abstract Property<String> getPackageName();

    /** Where the {@code .jxml} files are. Default {@code src/main/jxml}. A missing directory generates nothing. */
    public abstract DirectoryProperty getSourceDirectory();

    /** Where the Java is written. Default {@code build/generated/sources/jxforge}. */
    public abstract DirectoryProperty getOutputDirectory();

    /** Appended to each file name to name its class. Default {@code View}. */
    public abstract Property<String> getClassSuffix();

    /** Build subtrees without bindings once, when the class loads, and share them. Default true. */
    public abstract Property<Boolean> getHoistConstants();

    /** Version of {@code com.jxparallel:jxparallel-forge} to run. Default: the plugin's own version. */
    public abstract Property<String> getForgeVersion();
}
