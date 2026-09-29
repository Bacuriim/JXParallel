package com.jxparallel.ui.native2d;

/** GLFW key codes to JavaFX KeyCode names, so layers above native2d never see GLFW constants. */
final class JXKeys {
    private JXKeys() {
    }

    static String name(int glfwKey) {
        if (glfwKey >= 65 && glfwKey <= 90) {
            return String.valueOf((char) glfwKey);
        }
        if (glfwKey >= 48 && glfwKey <= 57) {
            return "DIGIT" + (char) glfwKey;
        }
        if (glfwKey >= 290 && glfwKey <= 313) {
            return "F" + (glfwKey - 289);
        }
        if (glfwKey >= 320 && glfwKey <= 329) {
            return "NUMPAD" + (glfwKey - 320);
        }
        switch (glfwKey) {
            case 32: return "SPACE";
            case 39: return "QUOTE";
            case 44: return "COMMA";
            case 45: return "MINUS";
            case 46: return "PERIOD";
            case 47: return "SLASH";
            case 59: return "SEMICOLON";
            case 61: return "EQUALS";
            case 91: return "OPEN_BRACKET";
            case 92: return "BACK_SLASH";
            case 93: return "CLOSE_BRACKET";
            case 96: return "BACK_QUOTE";
            case 256: return "ESCAPE";
            case 257: return "ENTER";
            case 258: return "TAB";
            case 259: return "BACK_SPACE";
            case 260: return "INSERT";
            case 261: return "DELETE";
            case 262: return "RIGHT";
            case 263: return "LEFT";
            case 264: return "DOWN";
            case 265: return "UP";
            case 266: return "PAGE_UP";
            case 267: return "PAGE_DOWN";
            case 268: return "HOME";
            case 269: return "END";
            case 280: return "CAPS";
            case 281: return "SCROLL_LOCK";
            case 282: return "NUM_LOCK";
            case 283: return "PRINTSCREEN";
            case 284: return "PAUSE";
            case 330: return "DECIMAL";
            case 331: return "DIVIDE";
            case 332: return "MULTIPLY";
            case 333: return "SUBTRACT";
            case 334: return "ADD";
            case 335: return "ENTER";
            case 336: return "EQUALS";
            case 340:
            case 344: return "SHIFT";
            case 341:
            case 345: return "CONTROL";
            case 342:
            case 346: return "ALT";
            case 343:
            case 347: return "WINDOWS";
            case 348: return "CONTEXT_MENU";
            default: return "UNDEFINED";
        }
    }

    /** GLFW modifier bits to {@link JXInputEvent} bits. */
    static int modifiers(int glfwMods) {
        int m = 0;
        if ((glfwMods & 0x0001) != 0) {
            m |= JXInputEvent.SHIFT;
        }
        if ((glfwMods & 0x0002) != 0) {
            m |= JXInputEvent.CONTROL;
        }
        if ((glfwMods & 0x0004) != 0) {
            m |= JXInputEvent.ALT;
        }
        if ((glfwMods & 0x0008) != 0) {
            m |= JXInputEvent.META;
        }
        return m;
    }
}
