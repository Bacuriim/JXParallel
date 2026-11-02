package com.jxparallel.forge;

import java.net.URL;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** JXForge builds native screens; nothing of JavaFX, or of the layers built on it, may enter it. */
class ArchitectureTest {
    @Test
    void forgeDoesNotDependOnJavaFx() {
        URL location = JxmlView.class.getProtectionDomain().getCodeSource().getLocation();
        JavaClasses forge = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importUrl(location);
        noClasses().should().dependOnClassesThat().resideInAnyPackage("javafx..", "com.sun.javafx..",
                        "com.jxparallel.fx..", "com.jxparallel.javafx..", "com.jxparallel.fxml..")
                .because("JXML and JXForge never use JavaFX")
                .check(forge);
    }
}
