package com.itao.workflowEngine.nodes.agent;

import com.itao.workflowEngine.agent.model.ToolExecutionResult;
import com.itao.workflowEngine.agent.tool.ToolExecutor;
import com.itao.workflowEngine.agent.tool.ToolRegistry;
import com.itao.workflowEngine.callback.BaseCallback;
import com.itao.workflowEngine.common.BaseNodeData;
import com.itao.workflowEngine.edges.EdgeBase;
import com.itao.workflowEngine.graph.GraphState;
import com.itao.workflowEngine.nodes.BaseNode;
import com.itao.util.spring.SpringBeanProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolNode extends BaseNode {

    private final ToolRegistry toolRegistry;

    public ToolNode(BaseNodeData nodeData, String workflowId, String userId, GraphState graphState,
                    List<EdgeBase> targetEdges, List<EdgeBase> sourceEdges, int maxSteps, BaseCallback callback) {
        super(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
        this.toolRegistry = SpringBeanProvider.getBean(ToolRegistry.class);
    }

    @Override
    public Map<String, Object> _run(String uniqueId) {
        String toolName = String.valueOf(nodeParams.get("tool_name"));
        ToolExecutor executor = toolRegistry.getExecutor(toolName);
        if (executor == null) {
            throw new IllegalStateException("No tool executor registered for " + toolName);
        }

        Map<String, Object> arguments = resolveArguments(toolName);

        Object delayValue = nodeParams.get("delay_ms");
        if (delayValue instanceof Number number && number.longValue() > 0) {
            sleep(number.longValue());
        }

        ToolExecutionResult executionResult = executor.execute(arguments, graphState);
        Map<String, Object> storedResult = new LinkedHashMap<>();
        storedResult.put("success", executionResult.isSuccess());
        storedResult.put("content", executionResult.getContent());
        storedResult.put("errorMessage", executionResult.getErrorMessage());
        storedResult.put("arguments", new LinkedHashMap<>(arguments));
        storedResult.put("structuredData", executionResult.getStructuredData());
        graphState.saveToolResult(toolName, storedResult);
        graphState.addAgentTrace(id, "tool_result", executionResult.getContent(), Map.of(
                "toolName", toolName,
                "success", executionResult.isSuccess(),
                "arguments", new LinkedHashMap<>(arguments)
        ));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool_name", toolName);
        result.put("message", executionResult.getContent());
        result.put("success", executionResult.isSuccess());
        result.put("arguments", new LinkedHashMap<>(arguments));
        result.put("structured_data", executionResult.getStructuredData());
        if (executionResult.getStructuredData() != null) {
            executionResult.getStructuredData().forEach(result::putIfAbsent);
        }
        return result;
    }

    private Map<String, Object> resolveArguments(String toolName) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        Object rawArguments = nodeParams.get("tool_args");
        if (rawArguments instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                arguments.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }

        Map<String, Object> pendingToolCall = graphState.consumePendingToolCall(id, toolName);
        if (pendingToolCall != null) {
            Object dynamicArguments = pendingToolCall.get("arguments");
            if (dynamicArguments instanceof Map<?, ?> rawDynamicMap) {
                for (Map.Entry<?, ?> entry : rawDynamicMap.entrySet()) {
                    arguments.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return arguments;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tool node interrupted", ex);
        }
    }
}
