package com.iyunwen.workflowEngine.utils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NodeParseLogUtils {

    private NodeParseLogUtils() {
    }

    public static Map<String, Object> createLogEntry(String key, Object value, String type) {
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("key", key);
        logEntry.put("value", value);
        logEntry.put("type", type);
        return logEntry;
    }
}
