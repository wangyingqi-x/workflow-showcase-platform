package com.itao.workflowEngine.agent.tool;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolDefinition {

    private String name;
    private String description;
    private Map<String, Object> inputSchema = defaultInputSchema();

    public ToolDefinition() {
    }

    public ToolDefinition(String name, String description) {
        this(name, description, null);
    }

    public ToolDefinition(String name, String description, Map<String, Object> inputSchema) {
        this.name = name;
        this.description = description;
        setInputSchema(inputSchema);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getInputSchema() {
        return new LinkedHashMap<>(inputSchema);
    }

    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema == null || inputSchema.isEmpty()
                ? defaultInputSchema()
                : new LinkedHashMap<>(inputSchema);
    }

    private static Map<String, Object> defaultInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
        schema.put("additionalProperties", true);
        return schema;
    }
}
