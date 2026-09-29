package com.jxparallel.fx.nativeimpl;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.jxparallel.fx.Fx;
import com.jxparallel.ui.native2d.JXFileDialogs;
import com.jxparallel.ui.native2d.JXWindow;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.WindowEvent;
import javafx.util.Callback;
import javafx.util.Duration;

/**
 * Dialogs in native mode: Alert, Dialog, TextInputDialog and ChoiceDialog as native windows laid out
 * like Modena's DialogPane (header, graphic, content, button bar in the Windows order), the
 * operating system's file and folder choosers, and ControlsFX notifications as small windows in a
 * corner of the screen.
 */
final class NativeDialogs {
    private static final String BUTTON_ORDER = "L_E+U+FBXI_YNOCAH_R"; // ButtonBar.BUTTON_ORDER_WINDOWS
    private static final List<NativeScene> NOTIFICATIONS = new ArrayList<>();

    private NativeDialogs() {
    }

    static void register() {
        Native.register("Dialog.getDialogPane()", (self, m, a) -> Fx.jx(pane(m)));
        Native.register("Dialog.showAndWait()", (self, m, a) -> showAndWait(m));
        Native.register("Dialog.show()", (self, m, a) -> {
            show(m);
            return null;
        });
        Native.register("Dialog.close()", (self, m, a) -> {
            close(m);
            return null;
        });
        Native.register("Dialog.hide()", (self, m, a) -> {
            close(m);
            return null;
        });
        Native.register("Dialog.isShowing()", (self, m, a) -> {
            NativeModel stage = (NativeModel) m.state.get("stage");
            return stage != null && NativeRuntime.sceneOf(stage) != null;
        });
        Native.register("Dialog.initModality(Modality)", (self, m, a) -> set(m, "modality", a[0]));
        Native.register("Dialog.initOwner(Window)", (self, m, a) -> set(m, "owner", a[0]));
        Native.register("Dialog.initStyle(StageStyle)", (self, m, a) -> set(m, "style", a[0]));
        Native.register("Alert.getButtonTypes()", (self, m, a) -> Fx.jx(Native.list(pane(m), "buttonTypes")));
        Native.register("DialogPane.lookupButton(ButtonType)", (self, m, a) -> Fx.jx(button(m, (ButtonType) Fx.fx(a[0]))));
        Native.register("TextInputDialog.getEditor()", (self, m, a) -> Fx.jx(editor(m)));
        Native.register("TextInputDialog.getDefaultValue()", (self, m, a) -> NativeElements.string(m, "defaultValue"));
        Native.register("ChoiceDialog.getItems()", (self, m, a) -> Fx.jx(Native.list(m, "items")));
        Native.register("ChoiceDialog.getSelectedItem()", (self, m, a) -> {
            NativeModel combo = (NativeModel) m.state.get("combo");
            return Fx.jx(combo != null ? Native.value(combo, "value") : Native.value(m, "selectedItem"));
        });
        Native.register("ChoiceDialog.setSelectedItem(Object)", (self, m, a) -> set(m, "selectedItem", a[0]));
        Native.register("FileChooser.showOpenDialog(Window)", (self, m, a) -> {
            List<File> files = openFiles(m, false);
            return files.isEmpty() ? null : files.get(0);
        });
        Native.register("FileChooser.showOpenMultipleDialog(Window)", (self, m, a) -> {
            List<File> files = openFiles(m, true);
            return files.isEmpty() ? null : files;
        });
        Native.register("FileChooser.showSaveDialog(Window)", (self, m, a) -> {
            Filter f = filter(m);
            return JXFileDialogs.save(NativeElements.string(m, "title"), directory(m),
                    NativeElements.string(m, "initialFileName"), f.patterns, f.description);
        });
        Native.register("DirectoryChooser.showDialog(Window)", (self, m, a) -> JXFileDialogs.folder(NativeElements.string(m, "title"), directory(m)));
        registerNotifications();
    }

    private static Object set(NativeModel m, String name, Object value) {
        Native.property(m, name, Object.class).setValue(Fx.fx(value));
        return null;
    }

    // ---- dialogs -----------------------------------------------------------------------------

    /** The dialog's pane, created on first use with the Alert's button types and texts. */
    static NativeModel pane(NativeModel dialog) {
        NativeModel pane = NativeElements.model(Native.value(dialog, "dialogPane"));
        if (pane == null) {
            pane = model(new com.jxparallel.fx.scene.control.DialogPane());
            Native.property(dialog, "dialogPane", Object.class).setValue(pane);
            Object own = dialog.values.get("buttonTypes");
            if (own instanceof List) {
                Native.list(pane, "buttonTypes").addAll((List<?>) own);
            }
            if (dialog.is(javafx.scene.control.Alert.class) && Native.list(pane, "buttonTypes").isEmpty()) {
                Object type = Native.value(dialog, "alertType");
                if (type == AlertType.CONFIRMATION) {
                    Native.list(pane, "buttonTypes").addAll(ButtonType.OK, ButtonType.CANCEL);
                } else if (type != AlertType.NONE && type != null) {
                    Native.list(pane, "buttonTypes").add(ButtonType.OK);
                }
            }
            if (dialog.is(javafx.scene.control.TextInputDialog.class) || dialog.is(javafx.scene.control.ChoiceDialog.class)) {
                if (Native.list(pane, "buttonTypes").isEmpty()) {
                    Native.list(pane, "buttonTypes").addAll(ButtonType.OK, ButtonType.CANCEL);
                }
            }
        }
        return pane;
    }

    private static NativeModel model(Object jx) {
        return (NativeModel) ((Fx.Backed) jx).fxPeer();
    }

    /** The button of a button type, created once so lookupButton and the shown dialog share it. */
    @SuppressWarnings("unchecked")
    static NativeModel button(NativeModel pane, ButtonType type) {
        java.util.Map<ButtonType, NativeModel> buttons = (java.util.Map<ButtonType, NativeModel>) pane.state.computeIfAbsent("buttons",
                k -> new java.util.LinkedHashMap<>());
        NativeModel b = buttons.get(type);
        if (b == null) {
            b = model(new com.jxparallel.fx.scene.control.Button(type.getText()));
            ButtonData data = type.getButtonData();
            if (data != null && data.isDefaultButton()) {
                Native.property(b, "defaultButton", boolean.class).setValue(true);
            }
            if (data != null && data.isCancelButton()) {
                Native.property(b, "cancelButton", boolean.class).setValue(true);
            }
            buttons.put(type, b);
        }
        return b;
    }

    private static NativeModel editor(NativeModel dialog) {
        NativeModel editor = (NativeModel) dialog.state.get("editor");
        if (editor == null) {
            editor = model(new com.jxparallel.fx.scene.control.TextField());
            Native.property(editor, "text", String.class).setValue(NativeElements.string(dialog, "defaultValue"));
            dialog.state.put("editor", editor);
        }
        return editor;
    }

    private static Object showAndWait(NativeModel dialog) {
        NativeModel stage = show(dialog);
        NativeRuntime.showAndWait(stage);
        return Optional.ofNullable(Fx.jx(Native.value(dialog, "result")));
    }

    /** Builds the dialog window and shows it; returns its stage. */
    private static NativeModel show(NativeModel dialog) {
        NativeModel existing = (NativeModel) dialog.state.get("stage");
        if (existing != null && NativeRuntime.sceneOf(existing) != null) {
            return existing;
        }
        Native.property(dialog, "result", Object.class).setValue(null);
        NativeModel pane = pane(dialog);
        com.jxparallel.fx.scene.layout.VBox root = new com.jxparallel.fx.scene.layout.VBox();
        NativeModel rootModel = model(root);
        String header = headerText(dialog, pane);
        NativeModel graphic = graphic(dialog, pane);
        if (header != null && !header.isEmpty()) {
            com.jxparallel.fx.scene.layout.HBox bar = new com.jxparallel.fx.scene.layout.HBox(10);
            NativeModel barModel = model(bar);
            Native.property(barModel, "style", String.class).setValue(
                    "-fx-background-color: linear-gradient(#f2f2f2, #e4e4e4); -fx-padding: 10 12 10 12;");
            Native.property(barModel, "alignment", Object.class).setValue(javafx.geometry.Pos.CENTER_LEFT);
            NativeModel title = model(new com.jxparallel.fx.scene.control.Label(header));
            Native.property(title, "wrapText", boolean.class).setValue(true);
            Native.property(title, "font", Object.class).setValue(javafx.scene.text.Font.font(14));
            Native.property(title, "maxWidth", double.class).setValue(Double.MAX_VALUE);
            barModel.children.add(title);
            NativeElements.constraintSet(title, "HBox.hgrow", javafx.scene.layout.Priority.ALWAYS);
            if (graphic != null) {
                barModel.children.add(graphic);
            }
            rootModel.children.add(barModel);
            rootModel.children.add(model(new com.jxparallel.fx.scene.control.Separator()));
        }
        com.jxparallel.fx.scene.layout.HBox body = new com.jxparallel.fx.scene.layout.HBox(12);
        NativeModel bodyModel = model(body);
        Native.property(bodyModel, "padding", Object.class).setValue(new javafx.geometry.Insets(12, 12, 6, 12));
        Native.property(bodyModel, "alignment", Object.class).setValue(javafx.geometry.Pos.CENTER_LEFT);
        if ((header == null || header.isEmpty()) && graphic != null) {
            bodyModel.children.add(graphic);
        }
        NativeModel content = content(dialog, pane);
        if (content != null) {
            bodyModel.children.add(content);
            NativeElements.constraintSet(content, "HBox.hgrow", javafx.scene.layout.Priority.ALWAYS);
        }
        rootModel.children.add(bodyModel);
        NativeModel buttonBar = model(new com.jxparallel.fx.scene.control.ButtonBar());
        Native.property(buttonBar, "padding", Object.class).setValue(new javafx.geometry.Insets(6, 12, 12, 12));
        for (ButtonType type : ordered(Native.list(pane, "buttonTypes"))) {
            NativeModel b = button(pane, type);
            installClose(dialog, b, type);
            Native.list(buttonBar, "buttons").add(b);
        }
        rootModel.children.add(buttonBar);
        NativeModel stage = model(new com.jxparallel.fx.stage.Stage());
        dialog.state.put("stage", stage);
        NativeModel scene = model(new com.jxparallel.fx.scene.Scene(root));
        Native.property(stage, "scene", Object.class).setValue(scene);
        Native.property(stage, "title", String.class).setValue(titleText(dialog));
        Native.property(stage, "resizable", boolean.class).setValue(Boolean.TRUE.equals(Native.value(dialog, "resizable")));
        Object modality = Native.value(dialog, "modality");
        Native.property(stage, "modality", Object.class).setValue(modality == null ? Modality.APPLICATION_MODAL : modality);
        Native.property(stage, "owner", Object.class).setValue(Native.value(dialog, "owner"));
        Native.property(stage, "onCloseRequest", Object.class).setValue((EventHandler<WindowEvent>) e -> {
            ButtonType cancel = cancelType(pane);
            if (cancel == null) {
                e.consume(); // like Dialog: without a cancel button the close button does nothing
            } else {
                Native.property(dialog, "result", Object.class).setValue(convert(dialog, cancel));
            }
        });
        Native.property(stage, "onHidden", Object.class).setValue((EventHandler<WindowEvent>) e -> fireDialog(dialog, "onHidden",
                javafx.scene.control.DialogEvent.DIALOG_HIDDEN));
        fireDialog(dialog, "onShowing", javafx.scene.control.DialogEvent.DIALOG_SHOWING);
        NativeRuntime.show(stage);
        fireDialog(dialog, "onShown", javafx.scene.control.DialogEvent.DIALOG_SHOWN);
        return stage;
    }

    /** The button's action (after application filters and handlers) sets the result and closes, like DialogPane. */
    @SuppressWarnings("unchecked")
    private static void installClose(NativeModel dialog, NativeModel button, ButtonType type) {
        if (button.state.containsKey("closesDialog")) {
            return;
        }
        button.state.put("closesDialog", true);
        EventHandler<ActionEvent> close = e -> {
            if (e.isConsumed()) {
                return;
            }
            Native.property(dialog, "result", Object.class).setValue(convert(dialog, type));
            close(dialog);
        };
        Native.list(button, "eventHandlers").add(new Object[]{ActionEvent.ACTION, close});
    }

    /** The result for a button: the application's result converter, else the dialog kind's default. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convert(NativeModel dialog, ButtonType type) {
        Object converter = Native.value(dialog, "resultConverter");
        if (converter instanceof Callback) {
            try {
                return Fx.fx(((Callback) converter).call(type));
            } catch (RuntimeException e) {
                NativeRuntime.report(e);
                return null;
            }
        }
        boolean ok = type.getButtonData() == ButtonData.OK_DONE;
        if (dialog.is(javafx.scene.control.TextInputDialog.class)) {
            return ok ? NativeElements.string(editor(dialog), "text") : null;
        }
        if (dialog.is(javafx.scene.control.ChoiceDialog.class)) {
            NativeModel combo = (NativeModel) dialog.state.get("combo");
            return ok && combo != null ? Native.value(combo, "value") : null;
        }
        return type; // Alert and plain dialogs: the button type
    }

    private static void close(NativeModel dialog) {
        NativeModel stage = (NativeModel) dialog.state.get("stage");
        if (stage != null) {
            fireDialog(dialog, "onHiding", javafx.scene.control.DialogEvent.DIALOG_HIDING);
            NativeRuntime.hide(stage);
        }
    }

    private static ButtonType cancelType(NativeModel pane) {
        List<Object> types = Native.list(pane, "buttonTypes");
        for (Object o : types) {
            if (o instanceof ButtonType && ((ButtonType) o).getButtonData() != null && ((ButtonType) o).getButtonData().isCancelButton()) {
                return (ButtonType) o;
            }
        }
        return types.size() == 1 && types.get(0) instanceof ButtonType ? (ButtonType) types.get(0) : null;
    }

    @SuppressWarnings("unchecked")
    private static void fireDialog(NativeModel dialog, String property, javafx.event.EventType<javafx.scene.control.DialogEvent> type) {
        Object h = Native.value(dialog, property);
        if (h instanceof EventHandler) {
            try {
                // DialogEvent needs a JavaFX dialog; a stand-in makes it, the copy names the native one
                Event event = new javafx.scene.control.DialogEvent(eventSource(), type).copyFor(dialog, dialog);
                ((EventHandler<Event>) h).handle(event);
            } catch (RuntimeException e) {
                NativeRuntime.report(e);
            }
        }
    }

    private static javafx.scene.control.Dialog<?> eventSource;

    private static javafx.scene.control.Dialog<?> eventSource() {
        if (eventSource == null) {
            eventSource = new javafx.scene.control.Dialog<>();
        }
        return eventSource;
    }

    /** Buttons in ButtonBar's Windows order (by button data), keeping the given order within a kind. */
    static List<ButtonType> ordered(List<Object> types) {
        List<ButtonType> out = new ArrayList<>();
        for (Object o : types) {
            if (o instanceof ButtonType) {
                out.add((ButtonType) o);
            }
        }
        out.sort((a, b) -> Integer.compare(order(a), order(b)));
        return out;
    }

    private static int order(ButtonType type) {
        ButtonData data = type.getButtonData();
        if (data == null) {
            return BUTTON_ORDER.length();
        }
        int at = BUTTON_ORDER.indexOf(data.getTypeCode());
        return at < 0 ? BUTTON_ORDER.length() : at;
    }

    private static String titleText(NativeModel dialog) {
        if (dialog.values.containsKey("title")) {
            return NativeElements.string(dialog, "title");
        }
        return dialog.is(javafx.scene.control.Alert.class) ? resource(alertKey(dialog) + ".title") : "";
    }

    private static String headerText(NativeModel dialog, NativeModel pane) {
        if (dialog.values.containsKey("headerText")) {
            Object v = Native.value(dialog, "headerText");
            return v == null ? null : v.toString();
        }
        if (pane.values.containsKey("headerText")) {
            Object v = Native.value(pane, "headerText");
            return v == null ? null : v.toString();
        }
        if (dialog.is(javafx.scene.control.Alert.class)) {
            return resource(alertKey(dialog) + ".header");
        }
        if (dialog.is(javafx.scene.control.TextInputDialog.class) || dialog.is(javafx.scene.control.ChoiceDialog.class)) {
            return resource("Dialog.confirm.header");
        }
        return null;
    }

    private static String alertKey(NativeModel alert) {
        Object type = Native.value(alert, "alertType");
        if (type == AlertType.INFORMATION) {
            return "Dialog.info";
        }
        if (type == AlertType.WARNING) {
            return "Dialog.warning";
        }
        if (type == AlertType.ERROR) {
            return "Dialog.error";
        }
        return "Dialog.confirm";
    }

    /** JavaFX's own localized dialog texts ("Confirmation", "Confirmação"...), English if unavailable. */
    static String resource(String key) {
        for (String name : new String[]{"com.sun.javafx.scene.control.skin.resources.ControlResources"}) {
            try {
                Class<?> c = Class.forName(name);
                return (String) c.getMethod("getString", String.class).invoke(null, key);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // fall through to English
            }
        }
        if (key.endsWith(".title") || key.endsWith(".header")) {
            String kind = key.substring(7, key.lastIndexOf('.'));
            switch (kind) {
                case "info":
                    return "Information";
                case "warning":
                    return "Warning";
                case "error":
                    return "Error";
                default:
                    return "Confirmation";
            }
        }
        return key;
    }

    /** The alert icon: a colored disc with a glyph (Modena uses images of the same meaning). */
    private static NativeModel graphic(NativeModel dialog, NativeModel pane) {
        NativeModel own = NativeElements.model(Native.value(dialog, "graphic"));
        if (own == null) {
            own = NativeElements.model(Native.value(pane, "graphic"));
        }
        if (own != null || !dialog.is(javafx.scene.control.Alert.class)) {
            return own;
        }
        Object type = Native.value(dialog, "alertType");
        if (type == AlertType.NONE || type == null) {
            return null;
        }
        String color;
        String glyph;
        if (type == AlertType.INFORMATION) {
            color = "#2d6cdf";
            glyph = "i";
        } else if (type == AlertType.WARNING) {
            color = "#e8a318";
            glyph = "!";
        } else if (type == AlertType.ERROR) {
            color = "#d9342b";
            glyph = "×";
        } else {
            color = "#2d6cdf";
            glyph = "?";
        }
        com.jxparallel.fx.scene.layout.StackPane disc = new com.jxparallel.fx.scene.layout.StackPane();
        NativeModel discModel = model(disc);
        Native.property(discModel, "style", String.class).setValue("-fx-background-color: " + color
                + "; -fx-background-radius: 20; -fx-min-width: 40; -fx-min-height: 40; -fx-pref-width: 40;"
                + " -fx-pref-height: 40; -fx-max-width: 40; -fx-max-height: 40;");
        NativeModel label = model(new com.jxparallel.fx.scene.control.Label(glyph));
        Native.property(label, "style", String.class).setValue("-fx-text-fill: white; -fx-font-size: 22; -fx-font-weight: bold;");
        discModel.children.add(label);
        return discModel;
    }

    /** The dialog's content node, else a wrapping label with its content text (plus the editor or choice box). */
    private static NativeModel content(NativeModel dialog, NativeModel pane) {
        NativeModel own = NativeElements.model(Native.value(pane, "content"));
        if (own != null) {
            return own;
        }
        String text = dialog.values.containsKey("contentText") ? NativeElements.string(dialog, "contentText")
                : NativeElements.string(pane, "contentText");
        NativeModel label = model(new com.jxparallel.fx.scene.control.Label(text));
        Native.property(label, "wrapText", boolean.class).setValue(true);
        Native.property(label, "prefWidth", double.class).setValue(Math.max(300, Math.min(520,
                com.jxparallel.ui.text.JXTextEngine.get().width(text, 12) + 10)));
        if (dialog.is(javafx.scene.control.TextInputDialog.class) || dialog.is(javafx.scene.control.ChoiceDialog.class)) {
            com.jxparallel.fx.scene.layout.HBox row = new com.jxparallel.fx.scene.layout.HBox(10);
            NativeModel rowModel = model(row);
            Native.property(rowModel, "alignment", Object.class).setValue(javafx.geometry.Pos.CENTER_LEFT);
            Native.property(label, "prefWidth", double.class).setValue(-1.0);
            Native.property(label, "wrapText", boolean.class).setValue(false);
            rowModel.children.add(label);
            if (dialog.is(javafx.scene.control.TextInputDialog.class)) {
                NativeModel field = editor(dialog);
                rowModel.children.add(field);
                field.state.put("wantsFocus", true);
            } else {
                NativeModel combo = model(new com.jxparallel.fx.scene.control.ComboBox<Object>());
                Native.list(combo, "items").setAll(Native.list(dialog, "items"));
                Native.property(combo, "value", Object.class).setValue(Native.value(dialog, "selectedItem"));
                Native.property(combo, "minWidth", double.class).setValue(150.0);
                dialog.state.put("combo", combo);
                rowModel.children.add(combo);
            }
            return rowModel;
        }
        return label;
    }

    // ---- file choosers -----------------------------------------------------------------------

    static final class Filter {
        List<String> patterns = Collections.emptyList();
        String description;
    }

    static Filter filter(NativeModel chooser) {
        Filter f = new Filter();
        Object selected = Native.value(chooser, "selectedExtensionFilter");
        List<Object> all = Native.list(chooser, "extensionFilters");
        Object chosen = selected instanceof FileChooser.ExtensionFilter ? selected : all.isEmpty() ? null : all.get(0);
        if (chosen instanceof FileChooser.ExtensionFilter) {
            FileChooser.ExtensionFilter ef = (FileChooser.ExtensionFilter) chosen;
            f.patterns = new ArrayList<>(ef.getExtensions());
            f.description = ef.getDescription();
        }
        return f;
    }

    static File directory(NativeModel chooser) {
        Object dir = Native.value(chooser, "initialDirectory");
        return dir instanceof File ? (File) dir : null;
    }

    private static List<File> openFiles(NativeModel chooser, boolean multiple) {
        Filter f = filter(chooser);
        return JXFileDialogs.open(NativeElements.string(chooser, "title"), directory(chooser),
                NativeElements.string(chooser, "initialFileName"), f.patterns, f.description, multiple);
    }

    // ---- ControlsFX notifications ------------------------------------------------------------

    private static final String NOTIFICATIONS_JX = "com.jxparallel.fx.controlsfx.control.Notifications";

    private static void registerNotifications() {
        Native.register("Notifications.create()", (self, m, a) -> newNotifications());
        for (String builder : new String[]{"title(String)", "text(String)", "graphic(Node)", "position(Pos)",
                "hideAfter(Duration)", "onAction(EventHandler)", "owner(Object)", "action(Action[])"}) {
            String name = builder.substring(0, builder.indexOf('('));
            Native.register("Notifications." + builder, (self, m, a) -> {
                Native.property(m, name, Object.class).setValue(Fx.fx(a[0]));
                return self;
            });
        }
        Native.register("Notifications.darkStyle()", (self, m, a) -> {
            Native.property(m, "dark", boolean.class).setValue(true);
            return self;
        });
        Native.register("Notifications.hideCloseButton()", (self, m, a) -> {
            Native.property(m, "hideCloseButton", boolean.class).setValue(true);
            return self;
        });
        Native.register("Notifications.show()", (self, m, a) -> notify(m, null));
        Native.register("Notifications.showInformation()", (self, m, a) -> notify(m, AlertType.INFORMATION));
        Native.register("Notifications.showWarning()", (self, m, a) -> notify(m, AlertType.WARNING));
        Native.register("Notifications.showError()", (self, m, a) -> notify(m, AlertType.ERROR));
        Native.register("Notifications.showConfirm()", (self, m, a) -> notify(m, AlertType.CONFIRMATION));
    }

    private static Object newNotifications() {
        try {
            Class<?> jxType = Class.forName(NOTIFICATIONS_JX, false, NativeDialogs.class.getClassLoader());
            Class<?> fxType = Class.forName("org.controlsfx.control.Notifications", false, NativeDialogs.class.getClassLoader());
            NativeModel model = new NativeModel(fxType);
            Constructor<?> c = jxType.getDeclaredConstructor(Fx.Wrap.class, Object.class);
            c.setAccessible(true);
            Object jx = c.newInstance(Fx.WRAP, model);
            Fx.own(model, jx);
            return jx;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean notificationsShowing() {
        synchronized (NOTIFICATIONS) {
            return !NOTIFICATIONS.isEmpty();
        }
    }

    /** A notification window at the bottom right of the screen; it hides itself after hideAfter (5 s). */
    static Object notify(NativeModel n, AlertType type) {
        Runnable show = () -> {
            com.jxparallel.fx.scene.layout.HBox box = new com.jxparallel.fx.scene.layout.HBox(10);
            NativeModel boxModel = model(box);
            boolean dark = Boolean.TRUE.equals(Native.value(n, "dark"));
            Native.property(boxModel, "style", String.class).setValue(dark
                    ? "-fx-background-color: #333333; -fx-padding: 12;" : "-fx-background-color: #fafafa; -fx-border-color: #b5b5b5; -fx-padding: 12;");
            Native.property(boxModel, "alignment", Object.class).setValue(javafx.geometry.Pos.CENTER_LEFT);
            NativeModel graphic = NativeElements.model(Native.value(n, "graphic"));
            if (graphic == null && type != null) {
                NativeModel alert = model(new com.jxparallel.fx.scene.control.Alert((com.jxparallel.fx.scene.control.Alert.AlertType)
                        Fx.jx(type)));
                graphic = graphic(alert, pane(alert));
            }
            if (graphic != null) {
                boxModel.children.add(graphic);
            }
            com.jxparallel.fx.scene.layout.VBox texts = new com.jxparallel.fx.scene.layout.VBox(4);
            NativeModel textsModel = model(texts);
            String title = NativeElements.string(n, "title");
            String color = dark ? "white" : "#333333";
            if (!title.isEmpty()) {
                NativeModel t = model(new com.jxparallel.fx.scene.control.Label(title));
                Native.property(t, "style", String.class).setValue("-fx-font-weight: bold; -fx-text-fill: " + color + ";");
                textsModel.children.add(t);
            }
            NativeModel text = model(new com.jxparallel.fx.scene.control.Label(NativeElements.string(n, "text")));
            Native.property(text, "style", String.class).setValue("-fx-text-fill: " + color + ";");
            Native.property(text, "wrapText", boolean.class).setValue(true);
            Native.property(text, "maxWidth", double.class).setValue(300.0);
            textsModel.children.add(text);
            boxModel.children.add(textsModel);
            NativeModel stage = model(new com.jxparallel.fx.stage.Stage());
            NativeModel scene = model(new com.jxparallel.fx.scene.Scene(box));
            Native.property(stage, "scene", Object.class).setValue(scene);
            Native.property(stage, "style", Object.class).setValue(javafx.stage.StageStyle.UNDECORATED);
            Native.property(stage, "alwaysOnTop", boolean.class).setValue(true);
            Object action = Native.value(n, "onAction");
            if (action instanceof EventHandler) {
                Native.property(boxModel, "onMouseClicked", Object.class).setValue((EventHandler<javafx.scene.input.MouseEvent>) e -> {
                    @SuppressWarnings("unchecked")
                    EventHandler<ActionEvent> h = (EventHandler<ActionEvent>) action;
                    h.handle(new ActionEvent(n, n));
                });
            }
            stage.state.put("noFocus", true);
            double[] size = NativeRuntime.preferredSize(boxModel);
            int[] screen = Boolean.getBoolean("jx.headless") ? new int[] {0, 0, 1280, 800} : JXWindow.screenBounds();
            int index;
            synchronized (NOTIFICATIONS) {
                index = NOTIFICATIONS.size();
            }
            double x = screen[0] + screen[2] - size[0] - 16;
            double y = screen[1] + screen[3] - (size[1] + 12) * (index + 1) - 4;
            Native.property(stage, "x", double.class).setValue(x);
            Native.property(stage, "y", double.class).setValue(y);
            NativeRuntime.show(stage);
            NativeScene shown = NativeRuntime.sceneOf(stage);
            if (shown == null) {
                return;
            }
            synchronized (NOTIFICATIONS) {
                NOTIFICATIONS.add(shown);
            }
            Object after = Native.value(n, "hideAfter");
            double millis = after instanceof Duration ? ((Duration) after).toMillis() : 5000;
            if (!Double.isInfinite(millis)) {
                java.util.Timer timer = new java.util.Timer("JX notification", true);
                timer.schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> {
                            synchronized (NOTIFICATIONS) {
                                NOTIFICATIONS.remove(shown);
                            }
                            NativeRuntime.hide(stage);
                        });
                    }
                }, (long) millis);
            }
        };
        if (Platform.isFxApplicationThread()) {
            show.run();
        } else {
            Platform.runLater(show);
        }
        return null;
    }
}
