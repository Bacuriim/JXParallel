package com.jxparallel.forge.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/** The {@code jxforge { fxml { ... } }} block: the options of the Maven goal {@code fxml}. */
public abstract class FxmlSpec {
    /**
     * Convert {@code .fxml} files before generating (the Maven equivalent is declaring the {@code fxml} execution
     * before {@code generate}). Default {@code false}: {@code src/main/resources} is where every JavaFX project keeps
     * its FXML, so converting it is something the build asks for. {@code jxforgeFxml} can always be run by hand.
     */
    public abstract Property<Boolean> getEnabled();

    /** Where the {@code .fxml} files are, in any subfolders. Default {@code src/main/resources}. */
    public abstract DirectoryProperty getSourceDirectory();

    /** Where the converted {@code .jxml} files are written, which {@code jxforgeGenerate} then compiles. */
    public abstract DirectoryProperty getOutputDirectory();

    /** Fail the build when anything is not converted (notes such as fx:id do not fail it). Default {@code false}. */
    public abstract Property<Boolean> getStrict();

    /** A {@code .properties} file that resolves the {@code %key} values of the FXML. */
    public abstract RegularFileProperty getBundle();

    /** The class of the rows of each table or list, by fx:id ({@code *} for all). */
    public abstract MapProperty<String, String> getItemTypes();

    /** Where what could not be converted is written. */
    public abstract RegularFileProperty getReportFile();

    /** {@code itemType("orders", "com.acme.Order")}. */
    public void itemType(String id, String type) {
        getItemTypes().put(id, type);
    }
}
