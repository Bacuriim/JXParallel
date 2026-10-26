package com.jxparallel.ui.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JXEditBufferTest {
    @Test
    void typingAWordComposesItAndCommitsIt() {
        JXEditBuffer b = new JXEditBuffer("", 0, 0);
        b.setComposingText("h", 1);
        b.setComposingText("he", 1);
        b.setComposingText("hel", 1);
        assertEquals("hel", b.getText());
        assertTrue(b.isComposing());
        assertEquals(0, b.getComposingStart());
        assertEquals(3, b.getComposingEnd());
        assertEquals(3, b.getCaret());
        b.commitText("hello", 1);   // the prediction replaces the composing word
        assertEquals("hello", b.getText());
        assertFalse(b.isComposing());
        assertEquals(5, b.getCaret());
        b.commitText(" ", 1);
        assertEquals("hello ", b.getText());
    }

    @Test
    void composingReplacesTheSelectionFirst() {
        JXEditBuffer b = new JXEditBuffer("one two", 4, 7);
        b.setComposingText("2", 1);
        assertEquals("one 2", b.getText());
        assertEquals(4, b.getComposingStart());
        assertEquals(5, b.getComposingEnd());
    }

    @Test
    void newCursorIsRelativeToTheStartOrToTheEndOfTheText() {
        JXEditBuffer b = new JXEditBuffer("ab", 1, 1);
        b.commitText("XYZ", 1);
        assertEquals("aXYZb", b.getText());
        assertEquals(4, b.getCaret(), "1 is just after the text");

        b = new JXEditBuffer("ab", 1, 1);
        b.commitText("XYZ", 0);
        assertEquals(1, b.getCaret(), "0 is just before it");

        b = new JXEditBuffer("ab", 1, 1);
        b.commitText("XYZ", -1);
        assertEquals(0, b.getCaret());

        b = new JXEditBuffer("ab", 1, 1);
        b.commitText("XYZ", 3);
        assertEquals(5, b.getCaret(), "beyond the text: clamped to the end");
    }

    @Test
    void accentsComposeOverTheSameRegion() {
        JXEditBuffer b = new JXEditBuffer("caf", 3, 3);
        b.setComposingRegion(0, 3);
        b.setComposingText("café", 1);
        assertEquals("café", b.getText());
        assertEquals(4, b.getCaret());
        b.finishComposingText();
        assertFalse(b.isComposing());
        assertEquals("café", b.getText());
    }

    @Test
    void emptyComposingTextRemovesTheComposition() {
        JXEditBuffer b = new JXEditBuffer("", 0, 0);
        b.setComposingText("abc", 1);
        b.setComposingText("", 1);
        assertEquals("", b.getText());
        assertFalse(b.isComposing());
    }

    @Test
    void setComposingRegionTakesEitherOrderAndClamps() {
        JXEditBuffer b = new JXEditBuffer("hello", 5, 5);
        b.setComposingRegion(4, 1);
        assertEquals(1, b.getComposingStart());
        assertEquals(4, b.getComposingEnd());
        b.setComposingRegion(2, 99);
        assertEquals(5, b.getComposingEnd());
        b.setComposingRegion(3, 3);
        assertFalse(b.isComposing());
    }

    @Test
    void deleteSurroundingTextWorksAroundTheCursor() {
        JXEditBuffer b = new JXEditBuffer("abcdef", 3, 3);
        b.deleteSurroundingText(1, 2);
        assertEquals("abf", b.getText());
        assertEquals(2, b.getCaret());
        b.deleteSurroundingText(10, 10);
        assertEquals("", b.getText());
        assertEquals(0, b.getCaret());
    }

    @Test
    void deleteSurroundingTextKeepsTheSelection() {
        JXEditBuffer b = new JXEditBuffer("abcdef", 2, 4);
        b.deleteSurroundingText(1, 1);
        assertEquals("acdf", b.getText());
        assertEquals("cd", b.getSelectedText());
    }

    @Test
    void deleteInsideTheComposingWordShrinksIt() {
        JXEditBuffer b = new JXEditBuffer("hello", 5, 5);
        b.setComposingRegion(0, 5);
        b.deleteSurroundingText(1, 0);
        assertEquals("hell", b.getText());
        assertEquals(0, b.getComposingStart());
        assertEquals(4, b.getComposingEnd());
        b.deleteSurroundingText(4, 0);
        assertFalse(b.isComposing());
    }

    @Test
    void aSurrogatePairIsNotSplit() {
        String emoji = new String(Character.toChars(0x1F600));
        JXEditBuffer b = new JXEditBuffer("a" + emoji + "b", 3, 3);   // after the emoji
        b.deleteSurroundingText(1, 0);
        assertEquals("ab", b.getText());

        b = new JXEditBuffer("a" + emoji + "b", 1, 1);   // before it
        b.deleteSurroundingText(0, 1);
        assertEquals("ab", b.getText());

        b = new JXEditBuffer("a" + emoji + "b", 3, 3);
        b.deleteSurroundingTextInCodePoints(1, 0);
        assertEquals("ab", b.getText());

        b = new JXEditBuffer(emoji + emoji, 4, 4);
        b.backspace();
        assertEquals(emoji, b.getText());
    }

    @Test
    void textAroundTheCursor() {
        JXEditBuffer b = new JXEditBuffer("hello world", 6, 6);
        assertEquals("hello ", b.getTextBeforeCursor(50));
        assertEquals("llo ", b.getTextBeforeCursor(4));
        assertEquals("wor", b.getTextAfterCursor(3));
        b.setSelection(0, 5);
        assertEquals("hello", b.getSelectedText());
        assertEquals("", b.getTextBeforeCursor(3));
        assertEquals(" wo", b.getTextAfterCursor(3), "after the selection");
    }

    @Test
    void backspaceAndDeleteForwardActOnTheSelectionOrOneChar() {
        JXEditBuffer b = new JXEditBuffer("abc", 3, 3);
        b.backspace();
        assertEquals("ab", b.getText());
        b.setSelection(0, 2);
        b.backspace();
        assertEquals("", b.getText());

        b = new JXEditBuffer("abc", 0, 0);
        b.deleteForward();
        assertEquals("bc", b.getText());
        b.deleteForward();
        b.deleteForward();
        b.deleteForward();
        assertEquals("", b.getText());
    }

    @Test
    void setSelectionClampsAndKeepsTheDirection() {
        JXEditBuffer b = new JXEditBuffer("abc", 0, 0);
        b.setSelection(3, 1);
        assertEquals(3, b.getAnchor());
        assertEquals(1, b.getCaret());
        assertEquals(1, b.getSelectionStart());
        assertEquals(3, b.getSelectionEnd());
        b.setSelection(-4, 40);
        assertEquals(0, b.getAnchor());
        assertEquals(3, b.getCaret());
    }
}
