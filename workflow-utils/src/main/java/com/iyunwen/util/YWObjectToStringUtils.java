package com.iyunwen.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class YWObjectToStringUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private YWObjectToStringUtils() {
    }

    public static String convertObjectToString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return value.toString();
        }
    }
}
