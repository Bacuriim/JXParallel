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

    /**
     * The translation files (ARB or Android strings.xml; see JXForge). Default {@code src/main/l10n}. A missing directory
     * means no translations; with one, a typed {@code Strings} class is generated beside the screens.
     */
    public abstract DirectoryProperty getL10nDirectory();

    /** A missing or extra translation fails the build instead of being a warning. Default false. */
    public abstract Property<Boolean> getL10nStrict();

    /** Version of {@code com.jxparallel:jxparallel-forge} to run. Default: the plugin's own version. */
    public abstract Property<String> getForgeVersion();
}
