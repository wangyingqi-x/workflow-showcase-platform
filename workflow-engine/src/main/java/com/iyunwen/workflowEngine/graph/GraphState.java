package com.iyunwen.workflowEngine.graph;

import com.iyunwen.workflowEngine.common.enums.RoleType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GraphState {

    private final Map<String, Map<String, Object>> nodeVariables = new ConcurrentHashMap<>();
    private final List<Map<String, String>> chatContext = new ArrayList<>();

    public void setVariable(String nodeId, String name, Object value) {
        nodeVariables.computeIfAbsent(nodeId, key -> new ConcurrentHashMap<>()).put(name, value);
    }

    public Object getVariable(String nodeId, String name) {
        Map<String, Object> values = nodeVariables.get(nodeId);
        return values == null ? null : values.get(name);
    }

    public Object getVariableByStr(String variablePath) {
        if (variablePath == null) {
            return null;
        }
        String[] parts = variablePath.split("\\.");
        if (parts.length != 2) {
            return null;
        }
        return getVariable(parts[0], parts[1]);
    }

    public void saveContext(String message, RoleType roleType) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", roleType.getType());
        item.put("message", message);
        chatContext.add(item);
    }

    public Map<String, Map<String, Object>> snapshotVariables() {
        Map<String, Map<String, Object>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : nodeVariables.entrySet()) {
            snapshot.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return snapshot;
    }

    public void clear() {
        nodeVariables.clear();
        chatContext.clear();
    }
}
