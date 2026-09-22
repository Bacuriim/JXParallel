package com.jxparallel.examples;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class TraditionalJavaFxApp extends Application {
    @Override
    public void start(Stage stage) {
        TextField nameField = new TextField();
        nameField.setPromptText("Your name");

        Label status = new Label("Ready");
        Button button = new Button("Continue");
        button.setOnAction(event -> runTask(nameField.getText(), status, button));

        VBox root = new VBox(12, nameField, button, status);
        root.setPadding(new Insets(24));
        root.setPrefWidth(360);

        stage.setTitle("JavaFX - Traditional");
        stage.setScene(new Scene(root));
        stage.show();
    }

    private void runTask(String name, Label status, Button button) {
        button.setDisable(true);
        status.setText("Loading...");

        Task<String> task = new Task<String>() {
            @Override
            protected String call() throws Exception {
                Thread.sleep(350L);
                return "Hello, " + (name == null || name.trim().isEmpty() ? "JavaFX" : name.trim());
            }
        };
        task.setOnSucceeded(event -> {
            status.setText(task.getValue());
            button.setDisable(false);
        });
        task.setOnFailed(event -> {
            status.setText("Unable to complete operation");
            button.setDisable(false);
        });
        Thread worker = new Thread(task, "traditional-javafx-worker");
        worker.setDaemon(true);
        worker.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
