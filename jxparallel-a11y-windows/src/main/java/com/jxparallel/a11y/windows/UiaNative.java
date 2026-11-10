package com.jxparallel.a11y.windows;

import java.util.ArrayList;
import java.util.List;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

/** The Win32 entry points the adapter calls (UIAutomationCore, oleaut32, user32) and the callback shapes of the COM vtables. */
final class UiaNative {
    private UiaNative() {
    }

    // ---- constants (uiautomationcore.h / UIAutomationClient.h) --------------------------------------

    static final int WM_GETOBJECT = 0x003D;
    static final int WM_NCDESTROY = 0x0082;
    static final int UIA_ROOT_OBJECT_ID = -25;
    static final int GWLP_WNDPROC = -4;

    static final int PROVIDER_OPTIONS_SERVER_SIDE = 0x2;

    static final int S_OK = 0;
    static final int E_NOINTERFACE = 0x80004002;
    static final int E_FAIL = 0x80004005;
    static final int E_INVALIDARG = 0x80070057;
    static final int E_ACCESSDENIED = 0x80070005;
    static final int UIA_E_ELEMENTNOTAVAILABLE = 0x80040201;
    static final int UIA_E_NOTSUPPORTED = 0x80040204;

    // control types
    static final int CT_BUTTON = 50000, CT_CHECKBOX = 50002, CT_COMBOBOX = 50003, CT_EDIT = 50004, CT_HYPERLINK = 50005,
            CT_IMAGE = 50006, CT_LISTITEM = 50007, CT_LIST = 50008, CT_MENU = 50009, CT_MENUBAR = 50010, CT_MENUITEM = 50011,
            CT_PROGRESSBAR = 50012, CT_RADIOBUTTON = 50013, CT_SCROLLBAR = 50014, CT_SLIDER = 50015, CT_SPINNER = 50016,
            CT_STATUSBAR = 50017, CT_TAB = 50018, CT_TABITEM = 50019, CT_TEXT = 50020, CT_TOOLTIP = 50022, CT_TREE = 50023,
            CT_TREEITEM = 50024, CT_CUSTOM = 50025, CT_GROUP = 50026, CT_DATAITEM = 50029, CT_WINDOW = 50032, CT_PANE = 50033,
            CT_SEPARATOR = 50038;

    // property ids
    static final int P_BOUNDS = 30001, P_PROCESS_ID = 30002, P_CONTROL_TYPE = 30003, P_NAME = 30005, P_HAS_FOCUS = 30008,
            P_FOCUSABLE = 30009, P_ENABLED = 30010, P_AUTOMATION_ID = 30011, P_CLASS_NAME = 30012, P_HELP_TEXT = 30013,
            P_IS_CONTROL = 30016, P_IS_CONTENT = 30017, P_IS_PASSWORD = 30019, P_OFFSCREEN = 30022, P_FRAMEWORK_ID = 30024,
            P_EXPAND_STATE = 30070, P_SELECTED = 30079, P_TOGGLE_STATE = 30086, P_VALUE = 30045, P_RANGE_VALUE = 30047;

    // pattern ids
    static final int PAT_INVOKE = 10000, PAT_VALUE = 10002, PAT_RANGE = 10003, PAT_EXPAND = 10005, PAT_SELITEM = 10010,
            PAT_TOGGLE = 10015;

    // event ids
    static final int EV_FOCUS_CHANGED = 20005, EV_SELECTION_ITEM_SELECTED = 20012;

    // ---- libraries --------------------------------------------------------------------------------

    interface UiaCore extends StdCallLibrary {
        UiaCore I = Native.load("UIAutomationCore", UiaCore.class);

        long UiaReturnRawElementProvider(Pointer hwnd, long wParam, long lParam, Pointer provider);

        int UiaHostProviderFromHwnd(Pointer hwnd, PointerByReference provider);

        int UiaRaiseAutomationEvent(Pointer provider, int eventId);

        int UiaRaiseAutomationPropertyChangedEvent(Pointer provider, int propertyId, Var.ByValue oldValue, Var.ByValue newValue);

        int UiaRaiseStructureChangedEvent(Pointer provider, int type, Pointer runtimeId, int runtimeIdLength);

        int UiaRaiseNotificationEvent(Pointer provider, int kind, int processing, Pointer displayString, Pointer activityId);

        int UiaClientsAreListening();

        int UiaDisconnectProvider(Pointer provider);
    }

    interface OleAut32 extends StdCallLibrary {
        OleAut32 I = Native.load("oleaut32", OleAut32.class);

        Pointer SysAllocString(WString s);

        void SysFreeString(Pointer bstr);

        Pointer SafeArrayCreateVector(short vt, int lowerBound, int count);

        int SafeArrayPutElement(Pointer array, int[] index, Pointer value);
    }

    interface User32 extends StdCallLibrary {
        User32 I = Native.load("user32", User32.class);

        long SetWindowLongPtrW(Pointer hwnd, int index, WndProc proc);

        long SetWindowLongPtrW(Pointer hwnd, int index, long value);

        long CallWindowProcW(Pointer previous, Pointer hwnd, int msg, long wParam, long lParam);

        int IsWindow(Pointer hwnd);
    }

    /** A window procedure (64-bit: LRESULT, WPARAM and LPARAM are 64 bits). */
    interface WndProc extends StdCallLibrary.StdCallCallback {
        long call(Pointer hwnd, int msg, long wParam, long lParam);
    }

    /** A VARIANT passed by value to UiaRaiseAutomationPropertyChangedEvent (24 bytes on 64-bit Windows). */
    @Structure.FieldOrder({"vt", "r1", "r2", "r3", "u0", "u1"})
    public static class Var extends Structure {
        public short vt;
        public short r1;
        public short r2;
        public short r3;
        public long u0;
        public long u1;

        public static class ByValue extends Var implements Structure.ByValue {
        }

        static ByValue empty() {
            return new ByValue();
        }

        static ByValue bstr(String s) {
            ByValue v = new ByValue();
            v.vt = 8;
            v.u0 = Pointer.nativeValue(OleAut32.I.SysAllocString(new WString(s)));
            return v;
        }

        static ByValue bool(boolean b) {
            ByValue v = new ByValue();
            v.vt = 11;
            v.u0 = b ? 0xFFFF : 0;
            return v;
        }

        static ByValue int32(int i) {
            ByValue v = new ByValue();
            v.vt = 3;
            v.u0 = i;
            return v;
        }

        static ByValue real(double d) {
            ByValue v = new ByValue();
            v.vt = 5;
            v.u0 = Double.doubleToRawLongBits(d);
            return v;
        }
    }

    // ---- COM vtable callback shapes ----------------------------------------------------------------

    interface F1 extends StdCallLibrary.StdCallCallback {
        int call(Pointer self);
    }

    interface F2 extends StdCallLibrary.StdCallCallback {
        int call(Pointer self, Pointer a);
    }

    interface F3 extends StdCallLibrary.StdCallCallback {
        int call(Pointer self, Pointer a, Pointer b);
    }

    interface F3i extends StdCallLibrary.StdCallCallback {
        int call(Pointer self, int a, Pointer b);
    }

    interface FD extends StdCallLibrary.StdCallCallback {
        int call(Pointer self, double a);
    }

    interface FDD extends StdCallLibrary.StdCallCallback {
        int call(Pointer self, double x, double y, Pointer out);
    }

    /** Keeps every callback reachable for as long as the process lives: native code holds their addresses. */
    private static final List<Callback> KEEP = new ArrayList<Callback>();

    static Pointer function(Callback callback) {
        synchronized (KEEP) {
            KEEP.add(callback);
        }
        return CallbackReference.getFunctionPointer(callback);
    }

    // ---- writing results ----------------------------------------------------------------------------

    static Pointer bstr(String s) {
        return OleAut32.I.SysAllocString(new WString(s == null ? "" : s));
    }

    /** VT_EMPTY, VT_I4, VT_BOOL and VT_BSTR into a VARIANT the caller gave. */
    static void variantEmpty(Pointer v) {
        v.clear(24);
    }

    static void variantInt(Pointer v, int value) {
        v.clear(24);
        v.setShort(0, (short) 3);
        v.setInt(8, value);
    }

    static void variantBool(Pointer v, boolean value) {
        v.clear(24);
        v.setShort(0, (short) 11);
        v.setShort(8, (short) (value ? -1 : 0));
    }

    static void variantString(Pointer v, String value) {
        v.clear(24);
        v.setShort(0, (short) 8);
        v.setPointer(8, bstr(value));
    }

    /** A SAFEARRAY of 32-bit integers (for runtime ids). */
    static Pointer intArray(int... values) {
        Pointer psa = OleAut32.I.SafeArrayCreateVector((short) 3, 0, values.length);
        com.sun.jna.Memory item = new com.sun.jna.Memory(4);
        for (int i = 0; i < values.length; i++) {
            item.setInt(0, values[i]);
            OleAut32.I.SafeArrayPutElement(psa, new int[] {i}, item);
        }
        return psa;
    }
}
