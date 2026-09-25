package com.jxparallel.ui;

import java.net.URL;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.native2d.JXNativeNode;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Architecture rules checked on the compiled classes of jxparallel-core and jxparallel-ui.
 * Both modules share package names, so each module is imported from its own class folder or jar.
 */
class ArchitectureTest {
    private static final URL CORE_LOCATION = JXElement.class.getProtectionDomain().getCodeSource().getLocation();
    private static final URL UI_LOCATION = JXNativeNode.class.getProtectionDomain().getCodeSource().getLocation();
    private static final JavaClasses CORE = importFrom(CORE_LOCATION);
    private static final JavaClasses UI = importFrom(UI_LOCATION);
    private static final JavaClasses BOTH = importFrom(CORE_LOCATION, UI_LOCATION);

    private static JavaClasses importFrom(URL... locations) {
        return new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importUrls(Arrays.asList(locations));
    }

    @Test
    void runtimeModulesUseNeitherAwtSwingNorJavaFx() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("java.awt..", "javax.swing..", "javafx..")
                .because("the native UI runs without AWT and without JavaFX")
                .check(BOTH);
    }

    @Test
    void coreDoesNotDependOnRenderers() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("org.lwjgl..", "io.github.humbleui..")
                .because("jxparallel-core must run on any JVM without native libraries")
                .check(CORE);
    }

    @Test
    void onlyNative2dTalksToGlfwSkiaAndNanoVg() {
        noClasses().that().resideOutsideOfPackage("com.jxparallel.ui.native2d..")
                .should().dependOnClassesThat().resideInAnyPackage("io.github.humbleui..", "org.lwjgl.glfw..",
                        "org.lwjgl.opengl..", "org.lwjgl.nanovg..")
                .because("renderer choice (Skia on 64-bit, NanoVG on 32-bit) stays inside native2d")
                .check(UI);
    }

    @Test
    void layoutMeasuresTextOnlyThroughTheTextEngine() {
        noClasses().that().resideOutsideOfPackage("com.jxparallel.ui.text..")
                .should().dependOnClassesThat().resideInAnyPackage("org.lwjgl.util.harfbuzz..", "org.lwjgl.util.freetype..")
                .because("one text engine gives the same sizes to layout and both renderers")
                .check(UI);
    }

    @Test
    void packagesHaveNoCycles() {
        slices().matching("com.jxparallel.(*)..").should().beFreeOfCycles().check(CORE);
        slices().matching("com.jxparallel.ui.(*)..").should().beFreeOfCycles().check(UI);
    }
}
