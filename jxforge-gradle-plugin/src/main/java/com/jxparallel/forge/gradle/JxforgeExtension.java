package com.jxparallel.forge.gradle;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/**
 * The {@code jxforge { ... }} block. The names and defaults are those of the parameters of the Maven goals
 * {@code generate}, {@code xsd} and {@code fxml}, with Gradle's folders instead of {@code target/}.
 */
public abstract class JxforgeExtension {
    private final FxmlSpec fxml;

    @Inject
    public JxforgeExtension(ObjectFactory objects) {
        this.fxml = objects.newInstance(FxmlSpec.class);
    }

    /** Java package of the generated classes (required by {@code jxforgeGenerate}). */
    public abstract Property<String> getPackageName();

    /** Where the {@code .jxml} files are, in any subfolders. Default {@code src/main/jxml}. */
    public abstract DirectoryProperty getSourceDirectory();

    /** Where the Java is written, and added to the main source set. Default {@code build/generated/sources/jxforge/java/main}. */
    public abstract DirectoryProperty getOutputDirectory();

    /** {@code order-list.jxml} becomes {@code OrderListView}. Default {@code View}. */
    public abstract Property<String> getClassSuffix();

    /** Build subtrees without bindings once, when the class loads, and share them. Default {@code true}. */
    public abstract Property<Boolean> getHoistConstants();

    /** Where {@code jxforgeXsd} writes the schema. Default {@code build/jxml.xsd}; property {@code -Pjxforge.xsd=path}. */
    public abstract RegularFileProperty getXsdFile();

    public FxmlSpec getFxml() {
        return fxml;
    }

    public void fxml(Action<? super FxmlSpec> action) {
        action.execute(fxml);
    }
}
