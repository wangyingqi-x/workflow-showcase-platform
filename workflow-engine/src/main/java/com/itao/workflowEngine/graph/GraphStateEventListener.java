package com.itao.workflowEngine.graph;

import com.itao.workflowEngine.graph.model.InteractionContext;

import java.util.Map;

public interface GraphStateEventListener {

    default void onAgentTrace(Map<String, Object> traceItem) {
    }

    default void onToolCall(Map<String, Object> toolCall) {
    }

    default void onToolResult(String toolName, Map<String, Object> result) {
    }

    default void onInteractionState(InteractionContext interactionContext) {
    }
}
