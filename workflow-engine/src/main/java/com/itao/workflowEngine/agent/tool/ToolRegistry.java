package com.itao.workflowEngine.agent.tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry {

    private final Map<String, ToolDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, ToolExecutor> executors = new ConcurrentHashMap<>();

    public void register(ToolDefinition definition, ToolExecutor executor) {
        if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
            throw new IllegalArgumentException("Tool definition name cannot be blank.");
        }
        definitions.put(definition.getName(), definition);
        executors.put(definition.getName(), executor);
    }

    public ToolDefinition getDefinition(String name) {
        return definitions.get(name);
    }

    public ToolExecutor getExecutor(String name) {
        return executors.get(name);
    }

    public Collection<ToolDefinition> listDefinitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(ToolDefinition::getName))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
}
