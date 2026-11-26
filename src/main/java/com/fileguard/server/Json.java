package com.fileguard.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A very small JSON reader/writer, enough for the backend's simple messages.
 * Writing a library ourselves keeps the project free of extra dependencies.
 *
 * Values map to Java like this: object -> Map, array -> List, string -> String,
 * number -> Double, true/false -> Boolean, null -> null.
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------ write

    /** Converts maps, lists, strings, numbers, booleans and null into JSON text. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                writeValue(sb, list.get(i));
            }
            sb.append(']');
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003c"); // safe to embed in HTML pages
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ read

    /** Parses JSON text. Throws {@link IllegalArgumentException} for invalid input. */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        Object value = p.value();
        p.skipWhitespace();
        if (p.pos != text.length()) {
            throw p.error("Unexpected text after JSON value");
        }
        return value;
    }

    /** Parses text that must be a JSON object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return (Map<String, Object>) value;
    }

    /** Deeper nesting is rejected, so a body like [[[[...]]]] cannot overflow the stack. */
    static final int MAX_DEPTH = 32;

    private static final class Parser {
        private final String s;
        private int pos;
        private int depth;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        Object value() {
            skipWhitespace();
            if (pos >= s.length()) {
                throw error("Unexpected end of input");
            }
            char c = s.charAt(pos);
            if ((c == '{' || c == '[') && ++depth > MAX_DEPTH) {
                throw error("Nesting too deep (max " + MAX_DEPTH + ")");
            }
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                depth--;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("Expected a property name");
                }
                String key = string();
                skipWhitespace();
                expect(':');
                map.put(key, value());
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    depth--;
                    return map;
                }
                if (c != ',') {
                    throw error("Expected ',' or '}'");
                }
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                depth--;
                return list;
            }
            while (true) {
                list.add(value());
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    depth--;
                    return list;
                }
                if (c != ',') {
                    throw error("Expected ',' or ']'");
                }
            }
        }

        private String string() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > s.length()) {
                                throw error("Bad unicode escape");
                            }
                            try {
                                sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw error("Bad unicode escape");
                            }
                            pos += 4;
                        }
                        default -> throw error("Bad escape character");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Double number() {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            if (start == pos) {
                throw error("Unexpected character '" + s.charAt(pos) + "'");
            }
            try {
                return Double.valueOf(s.substring(start, pos));
            } catch (NumberFormatException e) {
                throw error("Invalid number");
            }
        }

        private Object literal(String word, Object result) {
            if (!s.startsWith(word, pos)) {
                throw error("Unexpected token");
            }
            pos += word.length();
            return result;
        }

        void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        private char next() {
            if (pos >= s.length()) {
                throw error("Unexpected end of input");
            }
            return s.charAt(pos++);
        }

        private void expect(char c) {
            if (next() != c) {
                throw error("Expected '" + c + "'");
            }
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + pos);
        }
    }
}
