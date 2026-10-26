package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXLiveNode;
import com.jxparallel.ui.JXLiveSession;
import com.jxparallel.ui.JXProps;

/** The host against a fake session (ui does not know JXForge): attach, refresh, and the remount when the root changes. */
class JXLiveHostTest {
    private static final class Fake implements JXLiveSession {
        final JXElement element;
        boolean attached;
        int refreshes;
        boolean rootChanges;
        Fake next;

        Fake(String type) {
            element = JXElement.of(type, JXProps.builder().build());
        }

        public JXElement element() {
            return element;
        }

        public boolean attach(JXLiveNode mounted) {
            attached = mounted.getSource() == element;
            return attached;
        }

        public boolean refresh() {
            refreshes++;
            return !rootChanges;
        }

        public JXLiveSession renew() {
            return next;
        }
    }

    @Test
    void mountsAttachesRefreshesAndRemountsWhenTheRootChanges() {
        Fake first = new Fake("column");
        JXLiveHost host = new JXLiveHost(first);
        assertTrue(first.attached);
        JXNativeNode root = host.root();
        assertSame(first, host.session());
        assertFalse(host.refresh());
        assertEquals(1, first.refreshes);
        assertSame(root, host.root());

        first.rootChanges = true;
        first.next = new Fake("row");
        assertTrue(host.refresh());
        assertNotSame(root, host.root());
        assertEquals("row", host.root().getType());
        assertSame(first.next, host.session());
        assertTrue(first.next.attached);
        assertEquals(1, host.remounts());
    }
}
