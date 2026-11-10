package com.jxparallel.ui.native2d;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.input.JXTextDocument;
import com.jxparallel.ui.text.JXTextEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characters outside the Basic Multilingual Plane (emoji, rare CJK) are two {@code char}s in a Java string but one
 * character to the user: typed, deleted, moved over, selected, clicked and wrapped as one.
 */
class JXSurrogateEditingTest {
    private static final int BACKSPACE = 259;
    private static final int DELETE = 261;
    private static final int RIGHT = 262;
    private static final int LEFT = 263;
    private static final String GRIN = "😀"; // U+1F600
    private static final String ROCKET = "🚀"; // U+1F680

    private final String[] model = {""};
    private final int[] changes = {0};
    private final Consumer<Object> apply = v -> {
        model[0] = (String) v;
        changes[0]++;
    };

    private JXNativeNode node(String type, String value) {
        model[0] = value;
        JXNativeNode node = JXNativeNode.createBackendNode(element(type));
        node.layoutForBackend(240, 80);
        node.caret = value.length();
        return node;
    }

    private JXElement element(String type) {
        return JXElement.of(type, JXProps.builder().set("value", model[0]).set("onChange", apply).build());
    }

    /** Sends the key and re-renders with the value the application got back, as a controlled input does. */
    private void key(JXNativeNode node, JXKeyEvent event) {
        node.dispatchKey(event);
        node.reconcile(element(node.getType()));
    }

    private void press(JXNativeNode node, int code) {
        key(node, new JXKeyEvent(code, '\0'));
    }

    @Test
    void aTypedSupplementaryCharacterIsOneEventAndIsInsertedWhole() {
        JXNativeNode node = node("input", "ab");
        node.caret = 1;

        key(node, JXKeyEvent.typed(0x1F600, 0));

        assertEquals("a" + GRIN + "b", model[0]);
        assertEquals(1, changes[0], "one change, never a lone surrogate in between");
        assertEquals(3, node.caret, "the caret is after both chars");
    }

    @Test
    void typedKeyEventsCarryTheCodePoint() {
        JXKeyEvent emoji = JXKeyEvent.typed(0x1F600, 0);
        assertEquals(0x1F600, emoji.getKeyCodePoint());
        assertTrue(Character.isHighSurrogate(emoji.getKeyChar()));
        JXKeyEvent latin = JXKeyEvent.typed('é', 0);
        assertEquals('é', latin.getKeyChar());
        assertEquals('é', latin.getKeyCodePoint());
        assertEquals('x', new JXKeyEvent(0, 'x').getKeyCodePoint());
    }

    @Test
    void backspaceAndDeleteRemoveAWholeCharacter() {
        JXNativeNode node = node("input", "a" + GRIN + "b");
        node.caret = 3; // after the emoji

        press(node, BACKSPACE);
        assertEquals("ab", model[0]);
        assertEquals(1, node.caret);

        model[0] = "a" + ROCKET + "b";
        node.reconcile(element("input"));
        node.caret = 1;
        press(node, DELETE);
        assertEquals("ab", model[0]);
    }

    @Test
    void theArrowsStepOverAWholeCharacter() {
        JXNativeNode node = node("input", "a" + GRIN + "b");
        node.caret = 1;

        press(node, RIGHT);
        assertEquals(3, node.caret);
        press(node, LEFT);
        assertEquals(1, node.caret);
        press(node, LEFT);
        assertEquals(0, node.caret);
    }

    @Test
    void shiftArrowSelectsAWholeCharacter() {
        JXNativeNode node = node("input", GRIN + GRIN);
        node.caret = 0;

        key(node, new JXKeyEvent(RIGHT, '\0', JXInputEvent.SHIFT));
        assertEquals(2, node.caret);
        assertEquals(0, node.anchor);
        key(node, JXKeyEvent.typed('x', 0)); // typing replaces the selection

        assertEquals("x" + GRIN, model[0]);
    }

    @Test
    void aTextAreaMovesAndDeletesTheSameWay() {
        JXNativeNode node = node("textarea", "a\n" + GRIN);
        node.caret = 4;

        press(node, BACKSPACE);

        assertEquals("a\n", model[0]);
    }

    @Test
    void clickingNeverPutsTheCaretBetweenTheHalves() {
        JXNativeNode node = node("input", "a" + GRIN + GRIN + "b");
        for (double x = 0; x <= 200; x += 0.5) {
            int index = JXTextHit.fieldIndex(node, x);
            assertFalse(index == 2 || index == 4, "x=" + x + " gave index " + index + " inside an emoji");
        }
    }

    @Test
    void wrappingAndEllipsisNeverCutAnEmoji() {
        String text = GRIN + GRIN + GRIN + GRIN + GRIN + GRIN;
        float one = JXTextEngine.get().width(GRIN, 12);
        List<String> lines = JXTextLayout.lines(text, one * 2 + 0.5f, 12, false);

        for (String line : lines) {
            assertTrue(line.isEmpty() || Character.isHighSurrogate(line.charAt(0)), "line starts with an emoji: " + line);
            assertTrue(line.isEmpty() || Character.isLowSurrogate(line.charAt(line.length() - 1)), "line ends with one");
        }
        assertEquals(text, String.join("", lines));
        // an available width that fits "one and a half" emoji: the ellipsis follows whole emoji only
        String cut = JXTextLayout.ellipsize(text, one * 1.5f + JXTextEngine.get().width("...", 12), 12, false);
        assertTrue(cut.endsWith("..."));
        String kept = cut.substring(0, cut.length() - 3);
        assertEquals(0, kept.length() % 2);
        assertTrue(kept.isEmpty() || Character.isLowSurrogate(kept.charAt(kept.length() - 1)));
    }

    @Test
    void aWordWithSupplementaryLettersIsSelectedByADoubleClickAsOneWord() {
        // U+1D400 MATHEMATICAL BOLD CAPITAL A is a letter outside the BMP
        String word = "𝐀𝐁";
        String text = "x " + word + " y";

        int[] bounds = JXNativeNode.wordAt(text, 4); // between the two letters

        assertEquals(2, bounds[0]);
        assertEquals(6, bounds[1]);
    }

    @Test
    void theTextDocumentDeletesAndMovesByCharacter() {
        JXTextDocument document = new JXTextDocument("a" + GRIN + "b");
        document.setCaret(3, false);
        document.backspace();
        assertEquals("ab", document.getText());
        assertEquals(1, document.getCaret());

        document.setText(ROCKET);
        document.setCaret(0, false);
        document.delete();
        assertEquals("", document.getText());

        document.setText(GRIN + GRIN);
        document.setCaret(0, false);
        document.setCaret(document.getCaret() + 1, false); // one step over the first emoji
        assertEquals(2, document.getCaret());
        document.setCaret(3, true); // inside the second one, moving forward: its far edge
        assertEquals(4, document.getCaret());
    }
}
