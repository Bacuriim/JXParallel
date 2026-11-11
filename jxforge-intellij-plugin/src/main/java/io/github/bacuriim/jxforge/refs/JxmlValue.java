package io.github.bacuriim.jxforge.refs;

import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of an attribute value that talks to the controller: {@code ${a.b}}, {@code @{a.b}} or, on an
 * {@code onXxx} attribute, {@code #handler} / {@code #handler(a, b)}. Offsets are relative to the start of the
 * value text (inside the quotes).
 */
public final class JxmlValue {
    public enum Kind { ONE_WAY, TWO_WAY, HANDLER }

    /** A piece of the value: a path segment, a handler name or an argument. */
    public static final class Seg {
        public final String text;
        public final int start;
        public final int end;

        Seg(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }

        /** True for {@code name()}, which calls the method as written. */
        public boolean isCall() {
            return text.endsWith("()");
        }

        /** The name without {@code ()}. */
        public String name() {
            return isCall() ? text.substring(0, text.length() - 2).trim() : text;
        }
    }

    public final Kind kind;
    public final boolean negate;
    /** Path segments of a binding. */
    public final List<Seg> segments = new ArrayList<>();
    /** The method of a handler. */
    public final @Nullable Seg handler;
    /** The arguments of a handler: loop variables. */
    public final List<Seg> args = new ArrayList<>();
    /** False while the closing brace or parenthesis is missing (the user is still typing). */
    public final boolean closed;
    /** Offset where the binding body starts, after {@code ${}. */
    public final int bodyStart;
    public final int bodyEnd;

    private JxmlValue(Kind kind, boolean negate, @Nullable Seg handler, boolean closed, int bodyStart, int bodyEnd) {
        this.kind = kind;
        this.negate = negate;
        this.handler = handler;
        this.closed = closed;
        this.bodyStart = bodyStart;
        this.bodyEnd = bodyEnd;
    }

    public static boolean isHandlerAttribute(String name) {
        return name.length() > 2 && name.startsWith("on") && Character.isUpperCase(name.charAt(2));
    }

    public static @Nullable JxmlValue parse(XmlAttribute attribute) {
        String value = attribute.getValue();
        return value == null ? null : parse(attribute.getName(), value);
    }

    public static @Nullable JxmlValue parse(String attributeName, String v) {
        if (v.startsWith("${") || v.startsWith("@{")) {
            Kind kind = v.charAt(0) == '$' ? Kind.ONE_WAY : Kind.TWO_WAY;
            boolean closed = v.endsWith("}") && v.length() >= 3;
            int end = closed ? v.length() - 1 : v.length();
            JxmlValue result = new JxmlValue(kind, false, null, closed, 2, end);
            int start = 2;
            // `!` negation, written only in <If test="${!x}">
            int i = start;
            while (i < end && Character.isWhitespace(v.charAt(i))) {
                i++;
            }
            boolean negate = kind == Kind.ONE_WAY && i < end && v.charAt(i) == '!';
            if (negate) {
                start = i + 1;
                result = new JxmlValue(kind, true, null, closed, 2, end);
            }
            split(v, start, end, '.', result.segments, false);
            return result;
        }
        if (isHandlerAttribute(attributeName) && v.trim().startsWith("#")) {
            int hash = v.indexOf('#');
            int nameStart = hash + 1;
            int paren = v.indexOf('(', nameStart);
            int nameEnd = paren < 0 ? v.length() : paren;
            Seg name = trim(v, nameStart, nameEnd);
            boolean closed = paren < 0 || v.trim().endsWith(")");
            JxmlValue result = new JxmlValue(Kind.HANDLER, false, name, closed, nameStart, v.length());
            if (paren >= 0) {
                int close = v.lastIndexOf(')');
                int argsEnd = close > paren ? close : v.length();
                split(v, paren + 1, argsEnd, ',', result.args, true);
            }
            return result;
        }
        return null;
    }

    private static void split(String v, int from, int to, char separator, List<Seg> out, boolean skipEmpty) {
        int start = from;
        for (int i = from; i <= to; i++) {
            if (i == to || v.charAt(i) == separator) {
                // `a.b()`: the parentheses stay with the segment
                Seg seg = trim(v, start, i);
                if (!(skipEmpty && seg.text.isEmpty())) {
                    out.add(seg);
                }
                start = i + 1;
            }
        }
    }

    private static Seg trim(String v, int from, int to) {
        int s = from;
        int e = to;
        while (s < e && Character.isWhitespace(v.charAt(s))) {
            s++;
        }
        while (e > s && Character.isWhitespace(v.charAt(e - 1))) {
            e--;
        }
        return new Seg(v.substring(s, e), s, e);
    }
}
