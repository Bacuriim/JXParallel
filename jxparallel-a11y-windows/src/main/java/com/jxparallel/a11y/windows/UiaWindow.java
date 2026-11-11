package com.jxparallel.a11y.windows;

import java.util.HashMap;
import java.util.Map;

import com.jxparallel.ui.accessibility.JXAccessibilityEvent;
import com.jxparallel.ui.accessibility.JXAccessibilityHost;
import com.jxparallel.ui.accessibility.JXAccessibleNode;
import com.jxparallel.ui.accessibility.JXAccessibleState;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

/** One attached window: the subclassed window procedure and the providers made for its nodes. */
final class UiaWindow {
    final JXAccessibilityHost host;
    final Pointer hwnd;
    private final Map<Long, UiaProvider> providers = new HashMap<Long, UiaProvider>();
    private UiaNative.WndProc procedure;
    private long previous;

    UiaWindow(JXAccessibilityHost host, Pointer hwnd) {
        this.host = host;
        this.hwnd = hwnd;
    }

    /** Provider of the node with this id (made on first use); the window's root is id 1. */
    UiaProvider provider(long id) {
        synchronized (providers) {
            UiaProvider p = providers.get(Long.valueOf(id));
            if (p == null) {
                p = new UiaProvider(this, id);
                providers.put(Long.valueOf(id), p);
            }
            return p;
        }
    }

    UiaProvider root() {
        return provider(1);
    }

    /** Display thread: the provider answers WM_GETOBJECT from now on. */
    void subclass() {
        procedure = (h, msg, wParam, lParam) -> {
            try {
                if (msg == UiaNative.WM_GETOBJECT && (int) lParam == UiaNative.UIA_ROOT_OBJECT_ID) {
                    return UiaNative.UiaCore.I.UiaReturnRawElementProvider(h, wParam, lParam, root().iface(UiaProvider.SIMPLE));
                }
                if (msg == UiaNative.WM_NCDESTROY) {
                    UiaNative.UiaCore.I.UiaReturnRawElementProvider(h, 0, 0, null);
                }
            } catch (Throwable t) {
                System.err.println("uia: WM_GETOBJECT failed: " + t);
            }
            return UiaNative.User32.I.CallWindowProcW(new Pointer(previous), h, msg, wParam, lParam);
        };
        previous = UiaNative.User32.I.SetWindowLongPtrW(hwnd, UiaNative.GWLP_WNDPROC, procedure);
        if (previous == 0) {
            procedure = null;
            throw new IllegalStateException("SetWindowLongPtr failed");
        }
    }

    /** Display thread: the window procedure is put back (unless the window is gone) and UI Automation lets go of the providers. */
    void unsubclass() {
        if (procedure == null) {
            return;
        }
        try {
            if (UiaNative.User32.I.IsWindow(hwnd) != 0) {
                UiaNative.UiaCore.I.UiaReturnRawElementProvider(hwnd, 0, 0, null);
                UiaNative.User32.I.SetWindowLongPtrW(hwnd, UiaNative.GWLP_WNDPROC, previous);
            }
        } catch (Throwable t) {
            System.err.println("uia: detach failed: " + t);
        }
        procedure = null;
    }

    boolean clientsListening() {
        try {
            return UiaNative.UiaCore.I.UiaClientsAreListening() != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The provider of the window itself, hosted by the HWND (get_HostRawElementProvider of the root). */
    Pointer hostProvider() {
        PointerByReference out = new PointerByReference();
        int hr = UiaNative.UiaCore.I.UiaHostProviderFromHwnd(hwnd, out);
        return hr == 0 ? out.getValue() : null;
    }

    void raise(JXAccessibilityEvent e) {
        UiaNative.UiaCore core = UiaNative.UiaCore.I;
        long id = e.getNodeId();
        JXAccessibleNode node = id == 0 ? null : host.find(id);
        switch (e.getKind()) {
            case FOCUS_CHANGED:
                if (node != null) {
                    core.UiaRaiseAutomationEvent(provider(id).iface(UiaProvider.SIMPLE), UiaNative.EV_FOCUS_CHANGED);
                }
                break;
            case VALUE_CHANGED:
                if (node != null) {
                    boolean range = node.hasRange() && node.getRole() != com.jxparallel.ui.accessibility.JXAccessibilityRole.TEXT_FIELD;
                    core.UiaRaiseAutomationPropertyChangedEvent(provider(id).iface(UiaProvider.SIMPLE),
                            range ? UiaNative.P_RANGE_VALUE : UiaNative.P_VALUE, UiaNative.Var.empty(),
                            range ? UiaNative.Var.real(node.getNow()) : UiaNative.Var.bstr(node.getValue()));
                }
                break;
            case NAME_CHANGED:
                if (node != null) {
                    core.UiaRaiseAutomationPropertyChangedEvent(provider(id).iface(UiaProvider.SIMPLE), UiaNative.P_NAME,
                            UiaNative.Var.empty(), UiaNative.Var.bstr(node.getName()));
                }
                break;
            case STATE_CHANGED:
                if (node != null) {
                    state(node, e.getState());
                }
                break;
            case SELECTION_CHANGED:
                if (node != null && e.isStateOn()) {
                    core.UiaRaiseAutomationEvent(provider(id).iface(UiaProvider.SIMPLE), UiaNative.EV_SELECTION_ITEM_SELECTED);
                }
                break;
            case STRUCTURE_CHANGED:
                if (node != null) {
                    core.UiaRaiseStructureChangedEvent(provider(id).iface(UiaProvider.SIMPLE), 2, null, 0); // children invalidated
                }
                break;
            case ANNOUNCEMENT:
                core.UiaRaiseNotificationEvent(root().iface(UiaProvider.SIMPLE), 4, e.isAssertive() ? 0 : 1,
                        UiaNative.bstr(e.getText()), UiaNative.bstr("jxparallel.announce"));
                break;
            default:
                break;
        }
    }

    private void state(JXAccessibleNode node, JXAccessibleState s) {
        if (s == null) {
            return;
        }
        Pointer p = provider(node.getId()).iface(UiaProvider.SIMPLE);
        UiaNative.UiaCore core = UiaNative.UiaCore.I;
        switch (s) {
            case DISABLED:
                core.UiaRaiseAutomationPropertyChangedEvent(p, UiaNative.P_ENABLED, UiaNative.Var.empty(),
                        UiaNative.Var.bool(!node.is(JXAccessibleState.DISABLED)));
                break;
            case CHECKED:
            case MIXED:
                core.UiaRaiseAutomationPropertyChangedEvent(p, UiaNative.P_TOGGLE_STATE, UiaNative.Var.empty(),
                        UiaNative.Var.int32(UiaProvider.toggleState(node)));
                break;
            case EXPANDED:
            case COLLAPSED:
                core.UiaRaiseAutomationPropertyChangedEvent(p, UiaNative.P_EXPAND_STATE, UiaNative.Var.empty(),
                        UiaNative.Var.int32(UiaProvider.expandState(node)));
                break;
            default:
                break;
        }
    }
}
