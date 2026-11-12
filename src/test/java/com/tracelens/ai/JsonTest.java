package com.tracelens.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTest {

    @Test
    void readsNestedObjectsAndArrays() {
        Object value = Json.parse("""
                {"content": [{"type": "text", "text": "Hi"}, {"type": "thinking"}],
                 "stop_reason": "end_turn", "usage": {"output_tokens": 12}, "ok": true, "none": null}""");

        Map<?, ?> map = (Map<?, ?>) value;
        List<?> content = (List<?>) map.get("content");
        assertEquals("Hi", ((Map<?, ?>) content.get(0)).get("text"));
        assertEquals("end_turn", map.get("stop_reason"));
        assertEquals(12.0, ((Map<?, ?>) map.get("usage")).get("output_tokens"));
        assertEquals(true, map.get("ok"));
        assertNull(map.get("none"));
    }

    @Test
    void readsEscapesInStrings() {
        assertEquals("line1\nsaid \"hi\" \\ / \u00e9",
                Json.parse("\"line1\\nsaid \\\"hi\\\" \\\\ \\/ \\u00e9\""));
    }

    @Test
    void readsEmptyObjectAndArrayAndNumbers() {
        assertEquals(Map.of(), Json.parse("{ }"));
        assertEquals(List.of(), Json.parse("[ ]"));
        assertEquals(-1.5e3, Json.parse("-1.5e3"));
    }

    @Test
    void quoteEscapesEverythingThatWouldBreakJson() {
        String tricky = "path C:\\logs\t\"quoted\"\nnext\u0001";

        String quoted = Json.quote(tricky);

        assertEquals("\"path C:\\\\logs\\t\\\"quoted\\\"\\nnext\\u0001\"", quoted);
        assertEquals(tricky, Json.parse(quoted)); // writing then reading gives the same text back
    }

    @Test
    void invalidJsonIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": }"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": 1"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"not closed"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1, 2] extra"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse(""));
    }
}
