package com.nxp.example.smartgreenhouse.services.mqtt;

/**
 * Minimal reader for the flat JSON objects used by the collaboration data contract.
 * Only top-level string and integer attributes are supported, which keeps the Java heap usage low.
 */
final class JsonLite {

    private JsonLite() {
    }

    static boolean hasKey(String json, String key) {
        return findValueStart(json, key) >= 0;
    }

    static String getString(String json, String key) {
        int start = findValueStart(json, key);

        if (start < 0 || start >= json.length() || json.charAt(start) != '"') {
            return null;
        }

        int index = start + 1;

        while (index < json.length()) {
            char character = json.charAt(index);

            if (character == '\\') {
                index += 2;
                continue;
            }

            if (character == '"') {
                return json.substring(start + 1, index);
            }

            index++;
        }

        return null;
    }

    static int getInt(String json, String key, int defaultValue) {
        int index = findValueStart(json, key);

        if (index < 0) {
            return defaultValue;
        }

        boolean negative = false;

        if (index < json.length() && json.charAt(index) == '-') {
            negative = true;
            index++;
        }

        long value = 0L;
        int digits = 0;

        while (index < json.length()) {
            char character = json.charAt(index);

            if (character < '0' || character > '9') {
                break;
            }

            value = value * 10L + (character - '0');
            digits++;

            if (value > Integer.MAX_VALUE) {
                return defaultValue;
            }

            index++;
        }

        if (digits == 0) {
            return defaultValue;
        }

        return (int) (negative ? -value : value);
    }

    /** Returns the index of the first character of the value of the given key, or -1. */
    private static int findValueStart(String json, String key) {
        if (json == null || key == null) {
            return -1;
        }

        String quotedKey = "\"" + key + "\"";
        int from = 0;

        while (true) {
            int keyIndex = json.indexOf(quotedKey, from);

            if (keyIndex < 0) {
                return -1;
            }

            int index = skipWhitespace(json, keyIndex + quotedKey.length());

            if (index < json.length() && json.charAt(index) == ':') {
                return skipWhitespace(json, index + 1);
            }

            from = keyIndex + quotedKey.length();
        }
    }

    private static int skipWhitespace(String json, int index) {
        while (index < json.length()) {
            char character = json.charAt(index);

            if (character != ' ' && character != '\t' && character != '\r' && character != '\n') {
                break;
            }

            index++;
        }

        return index;
    }
}
