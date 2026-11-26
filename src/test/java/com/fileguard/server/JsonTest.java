package com.fileguard.server;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void writesAndReadsBackNestedValues() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("text", "quote \" backslash \\ newline \n tab \t <b>");
        inner.put("n", 42);
        inner.put("ok", true);
        inner.put("none", null);
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("list", List.of(1, "two", inner));

        String json = Json.write(outer);
        assertTrue(json.contains("\\u003cb>"), "'<' must be escaped so JSON is safe inside HTML");

        Map<String, Object> back = Json.parseObject(json);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedInner = (Map<String, Object>) ((List<?>) back.get("list")).get(2);
        assertEquals(inner.get("text"), parsedInner.get("text"));
        assertEquals(42.0, parsedInner.get("n"));
        assertEquals(Boolean.TRUE, parsedInner.get("ok"));
        assertNull(parsedInner.get("none"));
    }

    @Test
    void parsesUnicodeEscapes() {
        assertEquals("café", Json.parseObject("{\"a\":\"caf\\u00e9\"}").get("a"));
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1} trailing"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"unterminated"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("[1,2]"));
    }
}
