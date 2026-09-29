package com.jxparallel.fx.nativeimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.event.ActionEvent;
import com.jxparallel.fx.scene.control.Alert;
import com.jxparallel.fx.scene.control.Button;
import com.jxparallel.fx.scene.control.ButtonBar;
import com.jxparallel.fx.scene.control.ButtonType;
import com.jxparallel.fx.scene.control.ChoiceDialog;
import com.jxparallel.fx.scene.control.Dialog;
import com.jxparallel.fx.scene.control.Label;
import com.jxparallel.fx.scene.control.TextInputDialog;
import com.jxparallel.fx.scene.layout.VBox;
import com.jxparallel.ui.native2d.JXInputEvent;

import javafx.application.Platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dialogs block in a nested event loop, like JavaFX, while the test clicks their buttons. */
class NativeDialogsTest extends NativeTestSupport {

    /** The newest window: the dialog just shown. */
    private static NativeScene top() {
        return NativeRuntime.SCENES.get(NativeRuntime.SCENES.size() - 1);
    }

    /** Clicks the dialog button with this text, after the dialog opened (runs inside its nested loop). */
    private static void clickLater(String text) {
        Platform.runLater(() -> {
            NativeScene s = top();
            s.render();
            for (java.util.Map.Entry<NativeModel, com.jxparallel.ui.native2d.JXNativeNode> e : s.nodes.entrySet()) {
                if (e.getKey().is(javafx.scene.control.Button.class) && text.equals(NativeElements.string(e.getKey(), "text"))) {
                    com.jxparallel.ui.native2d.JXNativeNode n = e.getValue();
                    double x = n.getX() + n.getWidth() / 2.0;
                    double y = n.getY() + n.getHeight() / 2.0;
                    NativeEvents.handle(s, JXInputEvent.pointer(JXInputEvent.Kind.PRESS, x, y, 0, 0, 1, true,
                            java.util.Collections.emptyList()));
                    NativeEvents.handle(s, JXInputEvent.pointer(JXInputEvent.Kind.RELEASE, x, y, 0, 0, 1, false,
                            java.util.Collections.emptyList()));
                    return;
                }
            }
            throw new AssertionError("no button " + text);
        });
    }

    @Test
    void confirmationAlertReturnsTheClickedButton() throws Exception {
        int windows = fx(() -> NativeRuntime.SCENES.size());
        Optional<ButtonType> result = fx(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Excluir?");
            clickLater(ButtonType.CANCEL.getText());
            return alert.showAndWait();
        });
        assertEquals(ButtonType.CANCEL, result.get());
        assertEquals(windows, (int) fx(() -> NativeRuntime.SCENES.size()), "the dialog window is closed");
    }

    @Test
    void alertWithCustomButtonsOrdersThemLikeWindows() throws Exception {
        List<ButtonType> ordered = fx(() -> NativeDialogs.ordered(new ArrayList<>(java.util.Arrays.asList(
                javafx.scene.control.ButtonType.CANCEL, javafx.scene.control.ButtonType.NO, javafx.scene.control.ButtonType.YES,
                javafx.scene.control.ButtonType.OK))).stream().map(b -> (ButtonType) com.jxparallel.fx.Fx.jx(b))
                .collect(java.util.stream.Collectors.toList()));
        assertEquals(java.util.Arrays.asList(ButtonType.YES, ButtonType.NO, ButtonType.OK, ButtonType.CANCEL), ordered);
    }

    @Test
    void aFilterOnTheOkButtonKeepsTheDialogOpen() throws Exception {
        List<String> steps = new ArrayList<>();
        Optional<ButtonType> result = fx(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Salvar?");
            Button ok = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
            ok.addEventFilter(ActionEvent.ACTION, e -> {
                steps.add("refused");
                e.consume();
                clickLater(ButtonType.CANCEL.getText()); // the dialog is still open: cancel it
            });
            clickLater(ButtonType.OK.getText());
            return alert.showAndWait();
        });
        assertEquals(java.util.Arrays.asList("refused"), steps);
        assertEquals(ButtonType.CANCEL, result.get());
    }

    @Test
    void textInputDialogReturnsTheEditorText() throws Exception {
        Optional<String> result = fx(() -> {
            TextInputDialog dialog = new TextInputDialog("inicial");
            dialog.setHeaderText("Nome");
            Platform.runLater(() -> dialog.getEditor().setText("digitado"));
            clickLater(ButtonType.OK.getText());
            return dialog.showAndWait();
        });
        assertEquals("digitado", result.get());
    }

    @Test
    void choiceDialogReturnsTheChosenItem() throws Exception {
        Optional<String> result = fx(() -> {
            ChoiceDialog<String> dialog = new ChoiceDialog<>("b", "a", "b", "c");
            clickLater(ButtonType.OK.getText());
            return dialog.showAndWait();
        });
        assertEquals("b", result.get());
    }

    @Test
    void dialogResultConverterMapsTheButton() throws Exception {
        Optional<Integer> result = fx(() -> {
            Dialog<Integer> dialog = new Dialog<>();
            dialog.setTitle("Número");
            dialog.getDialogPane().setContent(new Label("Escolha"));
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dialog.setResultConverter(b -> b == ButtonType.OK ? 42 : -1);
            clickLater(ButtonType.OK.getText());
            return dialog.showAndWait();
        });
        assertEquals(42, (int) result.get());
    }

    @Test
    void closingADialogWithoutCancelButtonIsRefused() throws Exception {
        AtomicReference<Boolean> stillOpen = new AtomicReference<>();
        Optional<ButtonType> result = fx(() -> {
            // YES and OK are not cancel buttons (NO would be one, and would let the dialog close)
            Alert alert = new Alert(Alert.AlertType.NONE);
            alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.OK);
            Platform.runLater(() -> {
                NativeScene s = top();
                NativeEvents.handle(s, JXInputEvent.window(JXInputEvent.Kind.CLOSE_REQUEST, 0, 0));
                stillOpen.set(s.showing);
            });
            clickLater(ButtonType.YES.getText());
            return alert.showAndWait();
        });
        assertTrue(stillOpen.get());
        assertEquals(ButtonType.YES, result.get());
    }

    @Test
    void closingADialogWithACancelButtonReturnsIt() throws Exception {
        Optional<ButtonType> result = fx(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Sair?");
            Platform.runLater(() -> NativeEvents.handle(top(), JXInputEvent.window(JXInputEvent.Kind.CLOSE_REQUEST, 0, 0)));
            return alert.showAndWait();
        });
        assertEquals(ButtonType.CANCEL, result.get());
    }

    @Test
    void modalDialogBlocksClicksOnTheWindowBelow() throws Exception {
        List<String> clicks = new ArrayList<>();
        Button[] behind = new Button[1];
        NativeScene main = show(fx(() -> {
            behind[0] = new Button("behind");
            behind[0].setOnAction(e -> clicks.add("behind"));
            return new VBox(behind[0]);
        }), 200, 100);
        com.jxparallel.ui.native2d.JXNativeNode n = node(main, behind[0]);
        fx(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "info");
            Platform.runLater(() -> {
                double x = n.getX() + 5;
                double y = n.getY() + 5;
                NativeEvents.handle(main, JXInputEvent.pointer(JXInputEvent.Kind.PRESS, x, y, 0, 0, 1, true, java.util.Collections.emptyList()));
                NativeEvents.handle(main, JXInputEvent.pointer(JXInputEvent.Kind.RELEASE, x, y, 0, 0, 1, false, java.util.Collections.emptyList()));
            });
            clickLater(ButtonType.OK.getText());
            alert.showAndWait();
            return null;
        });
        assertTrue(clicks.isEmpty(), "the modal dialog blocked the click");
        click(main, behind[0]);
        assertEquals(1, clicks.size(), "after the dialog closed the window works again");
        close(main);
    }

    @Test
    void localizedAlertTitlesComeFromJavaFx() throws Exception {
        assertFalse(NativeDialogs.resource("Dialog.info.title").startsWith("Dialog."));
        assertFalse(NativeDialogs.resource("Dialog.error.header").isEmpty());
    }

    @Test
    void dialogPaneIsCreatedOnceWithTheAlertButtons() throws Exception {
        Object[] panes = fx(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            return new Object[]{alert.getDialogPane(), alert.getDialogPane(), alert.getButtonTypes().size()};
        });
        assertTrue(panes[0] == panes[1]);
        assertEquals(1, panes[2], "a warning has an OK button");
    }

    @Test
    void buttonBarElementListsTheDialogButtons() throws Exception {
        ButtonBar bar = fx(() -> {
            ButtonBar b = new ButtonBar();
            b.getButtons().addAll(new Button("A"), new Button("B"));
            return b;
        });
        assertEquals(2, fx(() -> NativeElements.toElement(model(bar))).getChildren().size());
    }
}
