package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The user's edits reach the application through the onChange prop, with no window: nodes get events directly. */
class JXChangeEventsTest {
    private static final int BACKSPACE = 259;
    private static final int DELETE = 261;
    private static final int LEFT = 263;
    private static final int DOWN = 264;
    private static final int UP = 265;
    private static final int PAGE_UP = 266;
    private static final int HOME = 268;
    private static final int ENTER = 257;

    private final List<Object> received = new ArrayList<Object>();
    private final Consumer<Object> onChange = received::add;

    private JXElement element(String type, Object... props) {
        JXProps.Builder builder = JXProps.builder().set("onChange", onChange);
        for (int i = 0; i < props.length; i += 2) {
            builder.set((String) props[i], props[i + 1]);
        }
        return JXElement.of(type, builder.build());
    }

    private static JXNativeNode mount(JXElement element) {
        JXNativeNode node = JXNativeNode.createBackendNode(element);
        node.layoutForBackend(200, 30);
        return node;
    }

    private static void type(JXNativeNode node, String text) {
        for (char ch : text.toCharArray()) {
            node.dispatchKey(new JXKeyEvent(0, ch));
        }
    }

    private static void press(JXNativeNode node, int code) {
        node.dispatchKey(new JXKeyEvent(code, '\0'));
    }

    @Test
    void typingReportsTheWholeNewText() {
        JXNativeNode node = mount(element("input", "value", "ab"));
        type(node, "c");
        assertEquals("abc", received.get(0));
    }

    @Test
    void anInputIsControlledSoTypingContinuesFromWhatTheApplicationRendered() {
        String[] model = {""};
        Consumer<Object> apply = v -> model[0] = (String) v;
        JXNativeNode node = mount(JXElement.of("password", JXProps.builder().set("value", "").set("onChange", apply).build()));
        for (char ch : "hey".toCharArray()) {
            node.dispatchKey(new JXKeyEvent(0, ch));
            node.reconcile(JXElement.of("password", JXProps.builder().set("value", model[0]).set("onChange", apply).build()));
        }
        assertEquals("hey", model[0]);
        press(node, BACKSPACE);
        assertEquals("he", model[0]);
    }

    @Test
    void theCaretMovesWithArrowsHomeAndEdits() {
        String[] model = {"abc"};
        Consumer<Object> apply = v -> model[0] = (String) v;
        JXNativeNode node = mount(JXElement.of("input", JXProps.builder().set("value", "abc").set("onChange", apply).build()));
        press(node, HOME);
        type(node, "X");
        assertEquals("Xabc", model[0]);
        node.reconcile(JXElement.of("input", JXProps.builder().set("value", model[0]).set("onChange", apply).build()));
        press(node, DELETE);
        assertEquals("Xbc", model[0]);
        node.reconcile(JXElement.of("input", JXProps.builder().set("value", model[0]).set("onChange", apply).build()));
        press(node, LEFT); // before X: caret was after X, now at the start
        press(node, BACKSPACE); // nothing before the caret: no change reported
        assertEquals("Xbc", model[0]);
    }

    @Test
    void enterBreaksALineOnlyInATextArea() {
        mount(element("textarea", "value", "a")).dispatchKey(new JXKeyEvent(ENTER, '\0'));
        mount(element("input", "value", "a")).dispatchKey(new JXKeyEvent(ENTER, '\0'));
        assertEquals(1, received.size());
        assertEquals("a\n", received.get(0));
    }

    @Test
    void nothingIsReportedWhenNothingChanged() {
        JXNativeNode node = mount(element("input", "value", ""));
        press(node, BACKSPACE);
        press(node, DELETE);
        node.dispatchKey(new JXKeyEvent(0, '\u0001')); // a control character
        assertTrue(received.isEmpty());
    }

    @Test
    void clickingACheckBoxReportsTheNewState() {
        JXNativeNode off = mount(element("checkbox", "label", "x", "checked", Boolean.FALSE));
        off.dispatchPointer(new JXPointerEvent(5, 5, 0));
        JXNativeNode on = mount(element("checkbox", "label", "x", "checked", Boolean.TRUE));
        on.dispatchPointer(new JXPointerEvent(5, 5, 0));
        off.dispatchKey(new JXKeyEvent(0, ' '));
        assertEquals(java.util.Arrays.<Object>asList(Boolean.TRUE, Boolean.FALSE, Boolean.TRUE), received);
    }

    @Test
    void clickingASliderReportsTheValueUnderThePointer() {
        JXNativeNode slider = mount(element("slider", "min", 0.0, "max", 10.0, "value", 0.0));
        slider.dispatchPointer(new JXPointerEvent(slider.getX() + 7, 5, 0));
        slider.dispatchPointer(new JXPointerEvent(slider.getX() + slider.getWidth() / 2, 5, 0));
        slider.dispatchPointer(new JXPointerEvent(slider.getX() + slider.getWidth() + 50, 5, 0));
        assertEquals(Double.valueOf(0), received.get(0));
        assertEquals(5.0, (Double) received.get(1), 0.2);
        assertEquals(Double.valueOf(10), received.get(2));
    }

    @Test
    void arrowsStepASpinnerWithinItsLimits() {
        JXNativeNode spinner = mount(element("spinner", "value", "4", "min", 0.0, "max", 5.0));
        press(spinner, UP);
        press(spinner, UP);
        press(spinner, DOWN);
        assertEquals(java.util.Arrays.<Object>asList(5.0, 5.0, 3.0), received);
    }

    @Test
    void arrowsChooseTheNeighbourOptionOfASelect() {
        String[] options = {"A", "B", "C"};
        press(mount(element("select", "value", "A", "options", options)), DOWN);
        press(mount(element("select", "value", "C", "options", options)), DOWN); // already last
        press(mount(element("select", "value", "C", "options", options)), UP);
        press(mount(element("select", "value", "", "options", options)), DOWN);
        assertEquals(java.util.Arrays.<Object>asList("B", "B", "A"), received);
    }

    @Test
    void arrowsMoveADatePickerByDayAndPagesByMonth() {
        JXNativeNode picker = mount(element("datepicker", "value", "2026-01-31"));
        press(picker, UP);
        press(picker, DOWN);
        press(picker, PAGE_UP);
        assertEquals(java.util.Arrays.<Object>asList("2026-02-01", "2026-01-30", "2026-02-28"), received);
    }

    @Test
    void aDisabledInputOrOneWithoutAHandlerReportsNothing() {
        type(mount(element("input", "value", "", "disabled", Boolean.TRUE)), "a");
        mount(element("checkbox", "disabled", Boolean.TRUE)).dispatchPointer(new JXPointerEvent(5, 5, 0));
        type(mount(JXElement.of("input", JXProps.builder().set("value", "").build())), "a");
        assertTrue(received.isEmpty());
    }

    @Test
    void anOnChangeThatIsNotAConsumerIsIgnored() {
        type(mount(JXElement.of("input", JXProps.builder().set("value", "").set("onChange", "x").build())), "a");
        assertTrue(received.isEmpty());
    }
}
