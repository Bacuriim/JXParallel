package com.jxparallel.forge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader for ARB files: objects keep their order and remember the line of each key. Values are
 * {@link Obj}, {@link List}, {@link String}, {@link Double}, {@link Boolean} or null. Not for general use: it reads the
 * whole text, rejects trailing commas and comments, and reports a problem as a {@link JxmlException} with file and line.
 */
final class JxmlJson {
    /** A JSON object in file order. */
    static final class Obj extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
        final Map<String, Integer> lines = new LinkedHashMap<String, Integer>();

        int line(String key) {
            Integer line = lines.get(key);
            return line == null ? 0 : line.intValue();
        }
    }

    private final String s;
    private final String file;
    private int i;
    private int line = 1;

    private JxmlJson(String text, String file) {
        this.s = text;
        this.file = file;
    }

    /** Parses {@code text}, which must hold one JSON value; {@code file} names it in errors. */
    static Object parse(String text, String file) {
        JxmlJson json = new JxmlJson(text.startsWith("﻿") ? text.substring(1) : text, file);
        json.skip();
        Object value = json.value();
        json.skip();
        if (json.i < json.s.length()) {
            throw json.error("unexpected text after the end of the value");
        }
        return value;
    }

    private JxmlException error(String what) {
        return new JxmlException(file, line, "bad JSON: " + what);
    }

    private void skip() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\n') {
                line++;
            } else if (c != ' ' && c != '\t' && c != '\r') {
                return;
            }
            i++;
        }
    }

    private Object value() {
        if (i >= s.length()) {
            throw error("unexpected end of the text");
        }
        char c = s.charAt(i);
        if (c == '{') {
            return object();
        }
        if (c == '[') {
            return array();
        }
        if (c == '"') {
            return string();
        }
        if (s.startsWith("true", i)) {
            i += 4;
            return Boolean.TRUE;
        }
        if (s.startsWith("false", i)) {
            i += 5;
            return Boolean.FALSE;
        }
        if (s.startsWith("null", i)) {
            i += 4;
            return null;
        }
        if (c == '-' || c >= '0' && c <= '9') {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            try {
                return Double.valueOf(s.substring(start, i));
            } catch (NumberFormatException e) {
                throw error("bad number " + s.substring(start, i));
            }
        }
        throw error("unexpected character '" + c + "'");
    }

    private Obj object() {
        Obj out = new Obj();
        i++;
        skip();
        if (i < s.length() && s.charAt(i) == '}') {
            i++;
            return out;
        }
        while (true) {
            skip();
            if (i >= s.length() || s.charAt(i) != '"') {
                throw error("expected a key (a string in double quotes)");
            }
            int keyLine = line;
            String key = string();
            skip();
            if (i >= s.length() || s.charAt(i) != ':') {
                throw error("expected : after the key \"" + key + "\"");
            }
            i++;
            skip();
            if (out.containsKey(key)) {
                throw new JxmlException(file, keyLine, "bad JSON: the key \"" + key + "\" is twice in the same object");
            }
            Object value = value();
            out.put(key, value);
            out.lines.put(key, Integer.valueOf(keyLine));
            skip();
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
                continue;
            }
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return out;
            }
            throw error("expected , or } after the value of \"" + key + "\"");
        }
    }

    private List<Object> array() {
        List<Object> out = new ArrayList<Object>();
        i++;
        skip();
        if (i < s.length() && s.charAt(i) == ']') {
            i++;
            return out;
        }
        while (true) {
            skip();
            out.add(value());
            skip();
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
                continue;
            }
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return out;
            }
            throw error("expected , or ] in the array");
        }
    }

    private String string() {
        StringBuilder out = new StringBuilder();
        i++; // the opening quote
        while (true) {
            if (i >= s.length()) {
                throw error("the string is not closed");
            }
            char c = s.charAt(i++);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\n') {
                throw error("a line break inside a string (write \\n)");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (i >= s.length()) {
                throw error("the string is not closed");
            }
            char e = s.charAt(i++);
            switch (e) {
                case '"':
                case '\\':
                case '/':
                    out.append(e);
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'u':
                    if (i + 4 > s.length()) {
                        throw error("bad \\u escape");
                    }
                    try {
                        out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("bad \\u escape");
                    }
                    i += 4;
                    break;
                default:
                    throw error("bad escape \\" + e);
            }
        }
    }

    /** {@code text} as a JSON string literal, with the quotes. */
    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int k = 0; k < text.length(); k++) {
            char c = text.charAt(k);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }
}
