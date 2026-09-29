package com.jxparallel.fx.nativeimpl;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.function.UnaryOperator;

import com.jxparallel.fx.Fx;

import javafx.scene.control.TextFormatter;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.util.StringConverter;

/**
 * Text editing of native text inputs (TextField, PasswordField, TextArea, and the editors of
 * Spinner, DatePicker and editable ComboBox): caret and selection, insert and delete, clipboard,
 * and {@code TextFormatter} filters and value converters, as TextInputControl does.
 */
final class NativeText {
    private NativeText() {
    }

    /** A text input: TextField, PasswordField, TextArea (and ControlsFX's custom fields). */
    static boolean isText(NativeModel m) {
        return m.is(javafx.scene.control.TextInputControl.class)
                || m.type.startsWith("Custom") && m.type.endsWith("Field");
    }

    static int caret(NativeModel m) {
        Object c = m.state.get("caret");
        int length = NativeElements.string(m, "text").length();
        return c instanceof Integer ? Math.max(0, Math.min((Integer) c, length)) : length;
    }

    static int anchor(NativeModel m) {
        Object a = m.state.get("anchor");
        int length = NativeElements.string(m, "text").length();
        return a instanceof Integer ? Math.max(0, Math.min((Integer) a, length)) : caret(m);
    }

    /** Moves the caret; with {@code select} the anchor stays, making a selection. */
    static void moveCaret(NativeModel m, int position, boolean select) {
        int length = NativeElements.string(m, "text").length();
        int p = Math.max(0, Math.min(position, length));
        m.state.put("caret", p);
        if (!select) {
            m.state.put("anchor", p);
        }
        Native.property(m, "caretPosition", int.class).setValue(p);
        Native.property(m, "anchor", int.class).setValue(anchor(m));
        Native.changed(m);
    }

    static void selectAll(NativeModel m) {
        m.state.put("anchor", 0);
        m.state.put("caret", NativeElements.string(m, "text").length());
        Native.changed(m);
    }

    static String selectedText(NativeModel m) {
        String text = NativeElements.string(m, "text");
        int from = Math.min(caret(m), anchor(m));
        int to = Math.max(caret(m), anchor(m));
        return text.substring(from, to);
    }

    /** Editable itself, and for the editor of a spinner or combo box, its control is editable too. */
    static boolean editable(NativeModel m) {
        Object owner = m.state.get("editorOf");
        if (owner instanceof NativeModel && !Boolean.TRUE.equals(Native.value((NativeModel) owner, "editable"))) {
            return false;
        }
        return !Boolean.FALSE.equals(Native.value(m, "editable")) && !NativeRuntime.disabled(m);
    }

    /** Replaces the selection with {@code insert}, through the TextFormatter filter if any. */
    static void replaceSelection(NativeModel m, String insert) {
        int from = Math.min(caret(m), anchor(m));
        int to = Math.max(caret(m), anchor(m));
        replace(m, from, to, insert);
    }

    static void replace(NativeModel m, int from, int to, String insert) {
        if (!editable(m)) {
            return;
        }
        String text = NativeElements.string(m, "text");
        from = Math.max(0, Math.min(from, text.length()));
        to = Math.max(from, Math.min(to, text.length()));
        if (!m.is(javafx.scene.control.TextArea.class)) {
            insert = insert.replace("\r", "").replace("\n", "");
        }
        String next = text.substring(0, from) + insert + text.substring(to);
        int caret = from + insert.length();
        int anchor = caret;
        UnaryOperator<TextFormatter.Change> filter = filter(m);
        if (filter != null) {
            TextFormatter.Change change = change(m, text, from, to, insert, anchor, caret);
            if (change != null) {
                TextFormatter.Change result;
                try {
                    result = filter.apply(change);
                } catch (RuntimeException e) {
                    NativeRuntime.report(e);
                    return;
                }
                if (result == null) {
                    return; // rejected
                }
                next = result.getControlNewText();
                caret = result.getCaretPosition();
                anchor = result.getAnchor();
            }
        }
        Native.property(m, "text", String.class).setValue(next);
        m.state.put("caret", Math.max(0, Math.min(caret, next.length())));
        m.state.put("anchor", Math.max(0, Math.min(anchor, next.length())));
        Native.changed(m);
    }

    static void deleteBackward(NativeModel m, boolean word) {
        int caret = caret(m);
        if (caret != anchor(m)) {
            replaceSelection(m, "");
        } else if (caret > 0) {
            int from = word ? previousWord(NativeElements.string(m, "text"), caret) : caret - 1;
            replace(m, from, caret, "");
        }
    }

    static void deleteForward(NativeModel m, boolean word) {
        int caret = caret(m);
        String text = NativeElements.string(m, "text");
        if (caret != anchor(m)) {
            replaceSelection(m, "");
        } else if (caret < text.length()) {
            int to = word ? nextWord(text, caret) : caret + 1;
            replace(m, caret, to, "");
        }
    }

    static int previousWord(String text, int from) {
        int i = Math.max(0, from - 1);
        while (i > 0 && !Character.isLetterOrDigit(text.charAt(i))) {
            i--;
        }
        while (i > 0 && Character.isLetterOrDigit(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    static int nextWord(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isLetterOrDigit(text.charAt(i))) {
            i++;
        }
        while (i < text.length() && !Character.isLetterOrDigit(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /** Selects the word at {@code index} (double click). */
    static void selectWord(NativeModel m, int index) {
        String text = NativeElements.string(m, "text");
        int start = Math.max(0, Math.min(index, text.length()));
        int end = start;
        while (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) {
            start--;
        }
        while (end < text.length() && Character.isLetterOrDigit(text.charAt(end))) {
            end++;
        }
        m.state.put("anchor", start);
        m.state.put("caret", end);
        Native.changed(m);
    }

    /** Where copied text goes: the system clipboard, or one in memory without a display (tests). */
    interface TextClipboard {
        String get();

        void set(String text);
    }

    static final TextClipboard SYSTEM = new TextClipboard() {
        @Override
        public String get() {
            return Clipboard.getSystemClipboard().getString();
        }

        @Override
        public void set(String text) {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
        }
    };

    static TextClipboard clipboard = Boolean.getBoolean("jx.headless") ? memoryClipboard() : SYSTEM;

    static TextClipboard memoryClipboard() {
        String[] held = new String[1];
        return new TextClipboard() {
            @Override
            public String get() {
                return held[0];
            }

            @Override
            public void set(String text) {
                held[0] = text;
            }
        };
    }

    static void copy(NativeModel m) {
        String selected = selectedText(m);
        if (!selected.isEmpty() && !m.is(javafx.scene.control.PasswordField.class)) {
            clipboard.set(selected);
        }
    }

    static void cut(NativeModel m) {
        if (m.is(javafx.scene.control.PasswordField.class)) {
            return;
        }
        copy(m);
        replaceSelection(m, "");
    }

    static void paste(NativeModel m) {
        String text = clipboard.get();
        if (text != null) {
            replaceSelection(m, text);
        }
    }

    /** Commits the text of a field: TextFormatter value, then the value of a spinner/date picker/combo that owns it. */
    static void commit(NativeModel m) {
        Object formatter = Native.value(m, "textFormatter");
        if (formatter instanceof TextFormatter) {
            commitFormatter((TextFormatter<?>) formatter, NativeElements.string(m, "text"));
        }
        Object owner = m.state.get("editorOf");
        if (owner instanceof NativeModel) {
            commitOwner((NativeModel) owner, NativeElements.string(m, "text"));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void commitFormatter(TextFormatter formatter, String text) {
        StringConverter converter = formatter.getValueConverter();
        if (converter != null) {
            try {
                formatter.setValue(converter.fromString(text));
            } catch (RuntimeException e) {
                // JavaFX keeps the previous value when the text does not convert
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void commitOwner(NativeModel owner, String text) {
        if (owner.is(javafx.scene.control.Spinner.class)) {
            javafx.scene.control.SpinnerValueFactory factory = NativeImpls.factory(owner);
            if (factory != null && factory.getConverter() != null) {
                try {
                    Object value = factory.getConverter().fromString(text);
                    if (value != null) {
                        factory.setValue(value);
                    }
                } catch (RuntimeException e) {
                    // invalid input: the value factory keeps its value, the text is reset below
                }
                syncSpinner(owner, (NativeModel) owner.state.get("editor"), true);
            }
        } else if (owner.is(javafx.scene.control.DatePicker.class)) {
            Object converter = Native.value(owner, "converter");
            Object parsed;
            try {
                parsed = converter instanceof StringConverter ? ((StringConverter) converter).fromString(text)
                        : text.trim().isEmpty() ? null : LocalDate.parse(text.trim(), defaultDateFormat());
            } catch (RuntimeException e) {
                parsed = Native.value(owner, "value");
            }
            Native.property(owner, "value", Object.class).setValue(parsed);
            syncDatePicker(owner, (NativeModel) owner.state.get("editor"), true);
        } else if (owner.is(javafx.scene.control.ComboBox.class)) {
            Object converter = Native.value(owner, "converter");
            Object value = converter instanceof StringConverter ? ((StringConverter) converter).fromString(text) : text;
            Native.property(owner, "value", Object.class).setValue(value);
        }
    }

    // ---- editors of composite controls ------------------------------------------------------

    /** The text field inside a Spinner, DatePicker or editable ComboBox, created with the control (like JavaFX). */
    static NativeModel editor(NativeModel owner) {
        Object existing = owner.state.get("editor");
        if (existing instanceof NativeModel) {
            return (NativeModel) existing;
        }
        Object jx = owner.values.get("editor");
        NativeModel editor = NativeElements.model(jx);
        if (editor == null) {
            com.jxparallel.fx.scene.control.TextField field = new com.jxparallel.fx.scene.control.TextField();
            editor = (NativeModel) field.fxPeer();
            owner.values.put("editor", field);
        }
        editor.state.put("editorOf", owner);
        owner.state.put("editor", editor);
        return editor;
    }

    /** Shows the spinner value in its editor, unless the user is typing in it. */
    static void syncSpinner(NativeModel spinner, NativeModel editor) {
        syncSpinner(spinner, editor, false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void syncSpinner(NativeModel spinner, NativeModel editor, boolean force) {
        if (editor == null) {
            return;
        }
        javafx.scene.control.SpinnerValueFactory factory = NativeImpls.factory(spinner);
        if (factory == null) {
            return;
        }
        Object value = factory.getValue();
        if (!force && value == spinner.state.get("shownValue") && editor.values.containsKey("text")) {
            return;
        }
        spinner.state.put("shownValue", value);
        StringConverter converter = factory.getConverter();
        String text = value == null ? "" : converter != null ? converter.toString(value) : String.valueOf(value);
        Native.property(editor, "text", String.class).setValue(text);
        editor.state.put("caret", text.length());
        editor.state.put("anchor", text.length());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void syncDatePicker(NativeModel picker, NativeModel editor) {
        syncDatePicker(picker, editor, false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void syncDatePicker(NativeModel picker, NativeModel editor, boolean force) {
        if (editor == null) {
            return;
        }
        Object value = Native.value(picker, "value");
        if (!force && value == picker.state.get("shownValue") && editor.values.containsKey("text")) {
            return;
        }
        picker.state.put("shownValue", value);
        Object converter = Native.value(picker, "converter");
        String text = value == null ? "" : converter instanceof StringConverter ? ((StringConverter) converter).toString(value)
                : ((LocalDate) value).format(defaultDateFormat());
        Native.property(editor, "text", String.class).setValue(text == null ? "" : text);
        editor.state.put("caret", text == null ? 0 : text.length());
        editor.state.put("anchor", text == null ? 0 : text.length());
    }

    /** DatePicker's default converter: the locale's short date format with a four digit year, like JavaFX's. */
    static DateTimeFormatter defaultDateFormat() {
        java.util.Locale locale = java.util.Locale.getDefault(java.util.Locale.Category.FORMAT);
        String pattern = java.time.format.DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null,
                java.time.chrono.IsoChronology.INSTANCE, locale);
        if (!pattern.contains("yyyy") && !pattern.contains("uuuu")) {
            pattern = pattern.replaceAll("y+", "yyyy").replaceAll("u+", "uuuu");
        }
        return DateTimeFormatter.ofPattern(pattern, locale);
    }

    // ---- TextFormatter -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static UnaryOperator<TextFormatter.Change> filter(NativeModel m) {
        Object formatter = Native.value(m, "textFormatter");
        if (formatter instanceof TextFormatter) {
            return ((TextFormatter<?>) formatter).getFilter();
        }
        return null;
    }

    private static javafx.scene.control.TextField proxy;

    /**
     * A TextFormatter.Change for this edit. Its constructor is package-private and takes an accessor
     * of the control's current text (JavaFX 8u40+ FormatterAccessor), answered here from the native
     * field; a detached JavaFX TextField holding the same text is the change's control.
     */
    private static TextFormatter.Change change(NativeModel m, String text, int from, int to, String insert, int anchor, int caret) {
        try {
            if (proxy == null) {
                proxy = new javafx.scene.control.TextField();
            }
            proxy.setText(text);
            int controlAnchor = anchor(m);
            int controlCaret = caret(m);
            Class<?> accessorType = Class.forName("com.sun.javafx.scene.control.FormatterAccessor");
            Object accessor = java.lang.reflect.Proxy.newProxyInstance(accessorType.getClassLoader(), new Class<?>[]{accessorType},
                    (p, method, args) -> {
                        switch (method.getName()) {
                            case "getTextLength":
                                return text.length();
                            case "getText":
                                return text.substring((Integer) args[0], (Integer) args[1]);
                            case "getCaret":
                                return controlCaret;
                            case "getAnchor":
                                return controlAnchor;
                            case "hashCode":
                                return System.identityHashCode(p);
                            case "equals":
                                return p == args[0];
                            default:
                                return "FormatterAccessor(native)";
                        }
                    });
            for (Constructor<?> c : TextFormatter.Change.class.getDeclaredConstructors()) {
                if (c.getParameterTypes().length == 7) {
                    c.setAccessible(true);
                    return (TextFormatter.Change) c.newInstance(proxy, accessor, from, to, insert, anchor, caret);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            NativeRuntime.report(e);
        }
        return null;
    }

    /** JavaFX-side value of a JX object or JavaFX object. */
    static Object fx(Object value) {
        return Fx.fx(value);
    }
}
