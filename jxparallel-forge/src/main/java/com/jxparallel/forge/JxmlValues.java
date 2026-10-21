package com.jxparallel.forge;

/**
 * Conversions shared by the interpreter and the generated code, so both put the same boxed type
 * in every prop: the native renderer reads gaps and sizes only from a {@link Number}, flags only
 * from a {@link Boolean}, and its change detection compares props with {@code equals}.
 */
public final class JxmlValues {
    private JxmlValues() {
    }

    /** A literal attribute value in the type the renderer reads; throws on a malformed number. */
    public static Object literal(AttrType type, String text) {
        switch (type) {
            case STRING:
            case HANDLER:
                return text;
            case DOUBLE:
                return Double.valueOf(text.trim());
            case INT:
                return Integer.valueOf(text.trim());
            case BOOLEAN:
                String b = text.trim();
                if (!"true".equals(b) && !"false".equals(b)) {
                    throw new IllegalArgumentException("expected true or false");
                }
                return Boolean.valueOf(b);
            case INSETS:
                return insets(text);
            case STRINGS:
                return strings(text);
            case ELEMENT:
            case OBJECT:
                throw new IllegalArgumentException("must be a binding, such as ${header}");
            default:
                throw new IllegalStateException(type.name());
        }
    }

    /** What a {@code <Slot>} shows: an element as is, or a component's render; null shows nothing. */
    public static com.jxparallel.ui.JXElement asElement(Object value) {
        if (value == null || value instanceof com.jxparallel.ui.JXElement) {
            return (com.jxparallel.ui.JXElement) value;
        }
        if (value instanceof com.jxparallel.ui.JXComponent) {
            return ((com.jxparallel.ui.JXComponent) value).render();
        }
        throw new IllegalArgumentException("a slot takes a JXElement or a JXComponent, not " + value.getClass().getName());
    }

    /** One number for all sides (a {@link Double}), or 2 or 4 numbers like CSS (top right bottom left). */
    static Object insets(String text) {
        String[] parts = text.trim().split("[\\s,]+");
        if (parts.length == 1) {
            return Double.valueOf(parts[0]);
        }
        double[] sides = new double[4];
        if (parts.length == 2) {
            sides[0] = sides[2] = Double.parseDouble(parts[0]);
            sides[1] = sides[3] = Double.parseDouble(parts[1]);
        } else if (parts.length == 4) {
            for (int i = 0; i < 4; i++) {
                sides[i] = Double.parseDouble(parts[i]);
            }
        } else {
            throw new IllegalArgumentException("expected 1, 2 or 4 numbers");
        }
        return sides;
    }

    /** Comma-separated items, trimmed: {@code "A, B, C"}. */
    static String[] strings(String text) {
        if (text.trim().isEmpty()) {
            return new String[0];
        }
        String[] parts = text.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    public static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static Double asDouble(Object value) {
        return value instanceof Number ? Double.valueOf(((Number) value).doubleValue()) : Double.valueOf(asString(value));
    }

    public static Integer asInt(Object value) {
        return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : Integer.valueOf(asString(value));
    }

    public static Boolean asBoolean(Object value) {
        return value instanceof Boolean ? (Boolean) value : Boolean.valueOf(asString(value));
    }

    public static String[] asStrings(Object value) {
        if (value instanceof String[]) {
            return (String[]) value;
        }
        if (value instanceof java.util.Collection) {
            java.util.Collection<?> items = (java.util.Collection<?>) value;
            String[] out = new String[items.size()];
            int i = 0;
            for (Object item : items) {
                out[i++] = asString(item);
            }
            return out;
        }
        return value == null ? new String[0] : strings(asString(value));
    }

    /** A bound value converted to the attribute's type, as both the interpreter and generated code do. */
    public static Object bound(AttrType type, Object value) {
        switch (type) {
            case STRING:
                return asString(value);
            case DOUBLE:
                return asDouble(value);
            case INT:
                return asInt(value);
            case BOOLEAN:
                return asBoolean(value);
            case STRINGS:
                return asStrings(value);
            case INSETS:
                return value instanceof Number ? asDouble(value) : insets(asString(value));
            case ELEMENT:
                return asElement(value);
            case OBJECT:
                return value;
            default:
                throw new IllegalStateException(type.name());
        }
    }
}
