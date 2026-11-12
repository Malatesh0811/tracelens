package com.tracelens.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A very small JSON reader and writer, so TraceLens needs no JSON library.
 *
 * Reading turns JSON into plain Java objects:
 * object -> Map, array -> List, string -> String, number -> Double, true/false -> Boolean, null -> null.
 *
 * It is a recursive descent parser: one method per kind of value, and a value
 * can contain other values, so the methods call each other.
 */
public final class Json {

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    /** @throws IllegalArgumentException when the text is not valid JSON */
    public static Object parse(String text) {
        Json parser = new Json(text);
        parser.skipSpaces();
        Object value = parser.readValue();
        parser.skipSpaces();
        if (parser.pos != text.length()) {
            throw parser.error("unexpected text after the end");
        }
        return value;
    }

    /** Writes a Java String as a JSON string, with quotes and escapes: he said "hi" -> "he said \"hi\"". */
    public static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c)); // other control characters
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    // ---------------------------------------------------------------- reading

    private Object readValue() {
        if (pos >= text.length()) {
            throw error("unexpected end");
        }
        char c = text.charAt(pos);
        if (c == '{') {
            return readObject();
        } else if (c == '[') {
            return readArray();
        } else if (c == '"') {
            return readString();
        } else if (c == '-' || Character.isDigit(c)) {
            return readNumber();
        } else if (text.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        } else if (text.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        } else if (text.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw error("unexpected character '" + c + "'");
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new HashMap<>();
        pos++; // {
        skipSpaces();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipSpaces();
            if (peek() != '"') {
                throw error("expected a quoted key");
            }
            String key = readString();
            skipSpaces();
            expect(':');
            skipSpaces();
            map.put(key, readValue());
            skipSpaces();
            if (peek() == ',') {
                pos++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipSpaces();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipSpaces();
            list.add(readValue());
            skipSpaces();
            if (peek() == ',') {
                pos++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String readString() {
        StringBuilder out = new StringBuilder();
        pos++; // opening quote
        while (true) {
            if (pos >= text.length()) {
                throw error("string is not closed");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw error("string is not closed");
            }
            char escaped = text.charAt(pos++);
            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (pos + 4 > text.length()) {
                        throw error("bad \\u escape");
                    }
                    try {
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException e) {
                        throw error("bad \\u escape");
                    }
                    pos += 4;
                }
                default -> throw error("bad escape \\" + escaped);
            }
        }
    }

    private Double readNumber() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        try {
            return Double.parseDouble(text.substring(start, pos));
        } catch (NumberFormatException e) {
            throw error("bad number");
        }
    }

    private void skipSpaces() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("unexpected end");
        }
        return text.charAt(pos);
    }

    private void expect(char c) {
        if (peek() != c) {
            throw error("expected '" + c + "'");
        }
        pos++;
    }

    private IllegalArgumentException error(String problem) {
        return new IllegalArgumentException("Invalid JSON at position " + pos + ": " + problem);
    }
}
