package com.jxparallel.ui;

import com.jxparallel.ui.controls.JXControls;
import com.jxparallel.ui.layout.JXLayouts;
import com.jxparallel.ui.theme.JXTheme;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JXUiCoreTest {
    @Test
    void shouldMountAndPatchIndependentTree() {
        JXElement first = JXLayouts.column(8, JXControls.button("Save", null));
        JXNode node = JXNode.mount(first);
        JXElement second = JXLayouts.column(8,
                JXControls.button("Save", null),
                JXControls.input("", "Name"));

        node.patch(first, second);

        assertEquals("column", node.getType());
        assertEquals(2, node.getChildren().size());
        assertEquals("input", node.getChildren().get(1).getType());
    }

    @Test
    void shouldNotifyStateChanges() {
        JXState<Integer> state = new JXState<Integer>(0);
        AtomicInteger observed = new AtomicInteger();
        state.subscribe(observed::set);
        state.set(2);
        assertEquals(2, observed.get());
    }

    @Test
    void shouldExposeCleanThemeTokens() {
        JXTheme theme = JXTheme.clean();
        assertEquals("#2563eb", theme.color("primary"));
        assertTrue(theme.spacing("md") > 0);
    }
}
