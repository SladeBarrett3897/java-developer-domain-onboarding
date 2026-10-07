package dev.learning.domain;

import java.util.LinkedHashMap;
import java.util.Map;

final class Json {
    private Json() {}

    static String object(Map<String, ?> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append(quote(entry.getKey())).append(':');
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) json.append(value);
            else json.append(quote(String.valueOf(value)));
        }
        return json.append('}').toString();
    }

    static Map<String, String> flatObject(String json, String field) {
        int key = json.indexOf(quote(field));
        if (key < 0) return Map.of();
        int start = json.indexOf('{', key);
        if (start < 0) return Map.of();
        int end = matchingBrace(json, start);
        return parseStrings(json.substring(start + 1, end));
    }

    static boolean booleanField(String json, String field) {
        int key = json.indexOf(quote(field));
        if (key < 0) return false;
        int colon = json.indexOf(':', key);
        return json.substring(colon + 1).stripLeading().startsWith("true");
    }

    private static Map<String, String> parseStrings(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        int cursor = 0;
        while (cursor < body.length()) {
            int keyStart = body.indexOf('"', cursor);
            if (keyStart < 0) break;
            int keyEnd = stringEnd(body, keyStart + 1);
            int colon = body.indexOf(':', keyEnd);
            int valueStart = body.indexOf('"', colon);
            if (colon < 0 || valueStart < 0) break;
            int valueEnd = stringEnd(body, valueStart + 1);
            values.put(unescape(body.substring(keyStart + 1, keyEnd)),
                    unescape(body.substring(valueStart + 1, valueEnd)));
            cursor = valueEnd + 1;
        }
        return values;
    }

    private static int matchingBrace(String text, int start) {
        int depth = 0;
        boolean quoted = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) quoted = !quoted;
            if (!quoted && c == '{') depth++;
            if (!quoted && c == '}' && --depth == 0) return i;
        }
        throw new IllegalArgumentException("Invalid JSON object");
    }

    private static int stringEnd(String text, int start) {
        for (int i = start; i < text.length(); i++) {
            if (text.charAt(i) == '"' && text.charAt(i - 1) != '\\') return i;
        }
        throw new IllegalArgumentException("Invalid JSON string");
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
