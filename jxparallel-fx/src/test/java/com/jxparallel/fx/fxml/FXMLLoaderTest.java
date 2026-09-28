package com.jxparallel.fx.fxml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.CountDownLatch;

import com.jxparallel.fx.scene.layout.VBox;
import com.sun.javafx.application.PlatformImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Unchanged FXML (javafx imports, Scene Builder sentinels, fx:include) builds com.jxparallel.fx nodes. */
class FXMLLoaderTest {

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            PlatformImpl.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await();
    }

    @Test
    void javafxTagsBuildJxClassesIncludingIncludes() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("Outer.fxml"));
        VBox root = loader.load();
        OuterController controller = loader.getController();

        assertEquals("Title", controller.title.getText());
        assertEquals(3, (int) controller.spinner.getValue());
        assertSame(com.jxparallel.fx.scene.layout.GridPane.class, root.getChildren().get(1).getClass());
        assertNotNull(controller.innerController.field);
        assertSame(controller.inner, controller.innerController.field.getParent());
    }

    @Test
    void staticLoadAlsoMaps() throws Exception {
        Object root = FXMLLoader.load(getClass().getResource("Inner.fxml"));
        assertSame(com.jxparallel.fx.scene.layout.HBox.class, root.getClass());
    }
}
