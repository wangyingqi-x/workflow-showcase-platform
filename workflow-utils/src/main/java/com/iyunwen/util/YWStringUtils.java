package com.iyunwen.util;

public final class YWStringUtils {

    private YWStringUtils() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}
