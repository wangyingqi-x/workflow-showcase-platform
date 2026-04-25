package com.itao.workflowEngine.agent.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class AgentToolCall {

    private String id;
    private String toolName;
    private String targetNodeId;
    private Map<String, Object> arguments = new LinkedHashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments == null ? new LinkedHashMap<>() : new LinkedHashMap<>(arguments);
    }
}
