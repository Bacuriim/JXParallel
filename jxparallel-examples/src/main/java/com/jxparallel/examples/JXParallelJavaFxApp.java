package com.jxparallel.examples;

import com.jxparallel.core.JXParallel;
import com.jxparallel.javafx.JXParallelFx;
import com.jxparallel.javafx.controls.JXButton;
import com.jxparallel.javafx.controls.JXTextField;
import com.jxparallel.javafx.controls.JXVisualDensity;
import com.jxparallel.javafx.controls.JXVisualVariant;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class JXParallelJavaFxApp extends Application {
    @Override
    public void start(Stage stage) {
        JXTextField nameField = new JXTextField();
        nameField.setPromptText("Your name");
        nameField.setDensity(JXVisualDensity.COMFORTABLE);

        Label status = new Label("Ready");
        JXButton button = new JXButton("Continue");
        button.setVariant(JXVisualVariant.PRIMARY);
        button.setDensity(JXVisualDensity.COMFORTABLE);
        button.animateAppear();
        button.setOnAction(event -> runTask(nameField.getText(), status, button));

        VBox root = new VBox(12, nameField.node(), button.node(), status);
        root.setPadding(new Insets(24));
        root.setPrefWidth(360);

        stage.setTitle("JXParallel - Modern JavaFX");
        stage.setScene(new Scene(root));
        stage.show();
    }

    private void runTask(String name, Label status, JXButton button) {
        button.setDisable(true);
        status.setText("Loading...");

        JXParallel.background(() -> {
            Thread.sleep(350L);
            return "Hello, " + (name == null || name.trim().isEmpty() ? "JXParallel" : name.trim());
        }).thenAccept(value -> JXParallelFx.ui(() -> {
            status.setText(value);
            button.setDisable(false);
        })).exceptionally(error -> {
            JXParallelFx.ui(() -> {
                status.setText("Unable to complete operation");
                button.setDisable(false);
            });
            return null;
        });
    }

    @Override
    public void stop() {
        JXParallel.shutdownNow();
    }

    public static void main(String[] args) {
        JXParallel.start();
        launch(args);
    }
}
