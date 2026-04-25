package com.itao.workflowEngine.graph;

import com.itao.workflowEngine.agent.llm.AgentMessageStreamListener;
import com.itao.workflowEngine.common.enums.RoleType;
import com.itao.workflowEngine.graph.model.InteractionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

public class GraphState {

    private final Map<String, Map<String, Object>> nodeVariables = new ConcurrentHashMap<>();
    private final Queue<Map<String, String>> chatContext = new ConcurrentLinkedQueue<>();
    private final Queue<Map<String, Object>> agentTrace = new ConcurrentLinkedQueue<>();
    private final Queue<Map<String, Object>> toolCalls = new ConcurrentLinkedQueue<>();
    private final Map<String, Queue<Map<String, Object>>> pendingToolCalls = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> toolResults = new ConcurrentHashMap<>();
    private final Map<String, Queue<String>> nodeSignals = new ConcurrentHashMap<>();
    private final Map<String, Boolean> scheduledNodes = new ConcurrentHashMap<>();
    private final Map<String, Integer> nodeScheduleCounts = new ConcurrentHashMap<>();
    private volatile AgentMessageStreamListener agentMessageStreamListener;
    private volatile GraphStateEventListener graphStateEventListener;
    private volatile InteractionContext pendingInteraction;

    public void setVariable(String nodeId, String name, Object value) {
        nodeVariables.computeIfAbsent(nodeId, key -> new ConcurrentHashMap<>()).put(name, value);
    }

    public Object getVariable(String nodeId, String name) {
        Map<String, Object> values = nodeVariables.get(nodeId);
        return values == null ? null : values.get(name);
    }

    public void removeVariable(String nodeId, String name) {
        Map<String, Object> values = nodeVariables.get(nodeId);
        if (values == null) {
            return;
        }
        values.remove(name);
        if (values.isEmpty()) {
            nodeVariables.remove(nodeId, values);
        }
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
        appendContext(roleType == null ? null : roleType.getType(), message);
    }

    public void appendContext(String role, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role == null || role.isBlank() ? RoleType.HUMAN.getType() : role);
        item.put("message", message);
        chatContext.add(item);
    }

    public void appendContexts(List<Map<String, String>> contexts) {
        if (contexts == null) {
            return;
        }
        for (Map<String, String> context : contexts) {
            if (context == null) {
                continue;
            }
            appendContext(context.get("role"), context.get("message"));
        }
    }

    public void addAgentTrace(String nodeId, String phase, String message, Map<String, Object> payload) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("nodeId", nodeId);
        item.put("phase", phase);
        item.put("message", message);
        item.put("payload", payload == null ? Map.of() : new LinkedHashMap<>(payload));
        agentTrace.add(item);
        if (graphStateEventListener != null) {
            graphStateEventListener.onAgentTrace(new LinkedHashMap<>(item));
        }
    }

    public void addToolCall(String nodeId, String toolName, String callId, String targetNodeId, Map<String, Object> arguments) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("nodeId", nodeId);
        item.put("toolName", toolName);
        item.put("callId", callId);
        item.put("targetNodeId", targetNodeId);
        item.put("arguments", arguments == null ? Map.of() : new LinkedHashMap<>(arguments));
        toolCalls.add(item);
        if (targetNodeId != null && !targetNodeId.isBlank()) {
            pendingToolCalls.computeIfAbsent(targetNodeId, key -> new ConcurrentLinkedQueue<>()).add(copyToolCall(item));
        }
        if (graphStateEventListener != null) {
            graphStateEventListener.onToolCall(new LinkedHashMap<>(item));
        }
    }

    public Map<String, Object> consumePendingToolCall(String targetNodeId, String toolName) {
        if (targetNodeId == null || targetNodeId.isBlank()) {
            return null;
        }
        Queue<Map<String, Object>> queue = pendingToolCalls.get(targetNodeId);
        if (queue == null || queue.isEmpty()) {
            return null;
        }
        synchronized (queue) {
            for (Map<String, Object> item : queue) {
                Object currentToolName = item.get("toolName");
                if (toolName != null && !toolName.isBlank() && !toolName.equals(currentToolName)) {
                    continue;
                }
                Map<String, Object> matched = copyToolCall(item);
                if (!queue.remove(item)) {
                    continue;
                }
                if (queue.isEmpty()) {
                    pendingToolCalls.remove(targetNodeId, queue);
                }
                return matched;
            }
            if (queue.isEmpty()) {
                pendingToolCalls.remove(targetNodeId, queue);
            }
        }
        return null;
    }

    public void recordNodeSignal(String nodeId, String sourceNodeId) {
        if (nodeId == null || sourceNodeId == null) {
            return;
        }
        nodeSignals.computeIfAbsent(nodeId, key -> new ConcurrentLinkedQueue<>()).add(sourceNodeId);
    }

    public void markNodeScheduled(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        scheduledNodes.put(nodeId, Boolean.TRUE);
        nodeScheduleCounts.merge(nodeId, 1, Integer::sum);
    }

    public boolean wasNodeScheduled(String nodeId) {
        return nodeId != null && Boolean.TRUE.equals(scheduledNodes.get(nodeId));
    }

    public int getNodeScheduleCount(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return 0;
        }
        return nodeScheduleCounts.getOrDefault(nodeId, 0);
    }

    public List<String> getNodeSignals(String nodeId) {
        Queue<String> signals = nodeSignals.get(nodeId);
        return signals == null ? List.of() : new ArrayList<>(signals);
    }

    public void clearNodeSignals(String nodeId) {
        nodeSignals.remove(nodeId);
    }

    public void saveToolResult(String toolName, Map<String, Object> result) {
        Map<String, Object> safeResult = result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(result);
        toolResults.put(toolName, safeResult);
        if (graphStateEventListener != null) {
            graphStateEventListener.onToolResult(toolName, new LinkedHashMap<>(safeResult));
        }
    }

    public Map<String, Object> getToolResult(String toolName) {
        Map<String, Object> result = toolResults.get(toolName);
        return result == null ? null : new LinkedHashMap<>(result);
    }

    public AgentMessageStreamListener getAgentMessageStreamListener() {
        return agentMessageStreamListener;
    }

    public void setAgentMessageStreamListener(AgentMessageStreamListener agentMessageStreamListener) {
        this.agentMessageStreamListener = agentMessageStreamListener;
    }

    public GraphStateEventListener getGraphStateEventListener() {
        return graphStateEventListener;
    }

    public void setGraphStateEventListener(GraphStateEventListener graphStateEventListener) {
        this.graphStateEventListener = graphStateEventListener;
    }

    public InteractionContext getPendingInteraction() {
        return copyInteractionContext(pendingInteraction);
    }

    public void setPendingInteraction(InteractionContext interactionContext) {
        pendingInteraction = copyInteractionContext(interactionContext);
        if (graphStateEventListener != null) {
            graphStateEventListener.onInteractionState(copyInteractionContext(pendingInteraction));
        }
    }

    public void clearPendingInteraction() {
        pendingInteraction = null;
        if (graphStateEventListener != null) {
            graphStateEventListener.onInteractionState(null);
        }
    }

    public List<Map<String, Object>> snapshotAgentTrace() {
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Map<String, Object> item : agentTrace) {
            snapshot.add(new LinkedHashMap<>(item));
        }
        return snapshot;
    }

    public List<Map<String, Object>> snapshotToolCalls() {
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Map<String, Object> item : toolCalls) {
            snapshot.add(new LinkedHashMap<>(item));
        }
        return snapshot;
    }

    public Map<String, Map<String, Object>> snapshotToolResults() {
        Map<String, Map<String, Object>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : toolResults.entrySet()) {
            snapshot.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return snapshot;
    }

    public Map<String, List<Map<String, Object>>> snapshotPendingToolCalls() {
        Map<String, List<Map<String, Object>>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Queue<Map<String, Object>>> entry : pendingToolCalls.entrySet()) {
            List<Map<String, Object>> queue = new ArrayList<>();
            for (Map<String, Object> item : entry.getValue()) {
                queue.add(copyToolCall(item));
            }
            snapshot.put(entry.getKey(), queue);
        }
        return snapshot;
    }

    public Map<String, List<String>> snapshotNodeSignals() {
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Queue<String>> entry : nodeSignals.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return snapshot;
    }

    public List<String> snapshotScheduledNodes() {
        return new ArrayList<>(scheduledNodes.keySet());
    }

    public List<Map<String, String>> snapshotChatContext() {
        List<Map<String, String>> snapshot = new ArrayList<>();
        for (Map<String, String> item : chatContext) {
            snapshot.add(new LinkedHashMap<>(item));
        }
        return snapshot;
    }

    public void restoreSnapshot(Map<String, Map<String, Object>> nodeVariables,
                                List<Map<String, String>> chatContext,
                                List<Map<String, Object>> agentTrace,
                                List<Map<String, Object>> toolCalls,
                                Map<String, List<Map<String, Object>>> pendingToolCalls,
                                Map<String, Map<String, Object>> toolResults,
                                Map<String, List<String>> nodeSignals,
                                List<String> scheduledNodes,
                                InteractionContext pendingInteraction) {
        clear();
        if (nodeVariables != null) {
            for (Map.Entry<String, Map<String, Object>> entry : nodeVariables.entrySet()) {
                this.nodeVariables.put(entry.getKey(), copyConcurrentMap(entry.getValue()));
            }
        }
        if (chatContext != null) {
            for (Map<String, String> item : chatContext) {
                this.chatContext.add(new LinkedHashMap<>(item));
            }
        }
        if (agentTrace != null) {
            for (Map<String, Object> item : agentTrace) {
                this.agentTrace.add(new LinkedHashMap<>(item));
            }
        }
        if (toolCalls != null) {
            for (Map<String, Object> item : toolCalls) {
                this.toolCalls.add(copyToolCall(item));
            }
        }
        if (pendingToolCalls != null) {
            for (Map.Entry<String, List<Map<String, Object>>> entry : pendingToolCalls.entrySet()) {
                Queue<Map<String, Object>> queue = new ConcurrentLinkedQueue<>();
                for (Map<String, Object> item : entry.getValue()) {
                    queue.add(copyToolCall(item));
                }
                this.pendingToolCalls.put(entry.getKey(), queue);
            }
        }
        if (toolResults != null) {
            for (Map.Entry<String, Map<String, Object>> entry : toolResults.entrySet()) {
                this.toolResults.put(entry.getKey(), copyNullableMap(entry.getValue()));
            }
        }
        if (nodeSignals != null) {
            for (Map.Entry<String, List<String>> entry : nodeSignals.entrySet()) {
                this.nodeSignals.put(entry.getKey(), new ConcurrentLinkedQueue<>(entry.getValue()));
            }
        }
        if (scheduledNodes != null) {
            for (String nodeId : scheduledNodes) {
                if (nodeId != null && !nodeId.isBlank()) {
                    this.scheduledNodes.put(nodeId, Boolean.TRUE);
                }
            }
        }
        this.pendingInteraction = copyInteractionContext(pendingInteraction);
    }

    public Map<String, Map<String, Object>> snapshotVariables() {
        Map<String, Map<String, Object>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : nodeVariables.entrySet()) {
            snapshot.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return snapshot;
    }

    public InteractionContext snapshotInteractionContext() {
        return copyInteractionContext(pendingInteraction);
    }

    public void clear() {
        nodeVariables.clear();
        chatContext.clear();
        agentTrace.clear();
        toolCalls.clear();
        pendingToolCalls.clear();
        toolResults.clear();
        nodeSignals.clear();
        scheduledNodes.clear();
        nodeScheduleCounts.clear();
        pendingInteraction = null;
        agentMessageStreamListener = null;
        graphStateEventListener = null;
    }

    private Map<String, Object> copyToolCall(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if ("arguments".equals(entry.getKey()) && entry.getValue() instanceof Map<?, ?> rawArguments) {
                Map<String, Object> arguments = new LinkedHashMap<>();
                for (Map.Entry<?, ?> argumentEntry : rawArguments.entrySet()) {
                    arguments.put(String.valueOf(argumentEntry.getKey()), argumentEntry.getValue());
                }
                copy.put(entry.getKey(), arguments);
                continue;
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private Map<String, Object> copyConcurrentMap(Map<String, Object> source) {
        Map<String, Object> copy = new ConcurrentHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }

    private Map<String, Object> copyNullableMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private InteractionContext copyInteractionContext(InteractionContext source) {
        if (source == null) {
            return null;
        }
        InteractionContext copy = new InteractionContext();
        copy.setNodeId(source.getNodeId());
        copy.setNodeName(source.getNodeName());
        copy.setNodeType(source.getNodeType());
        copy.setInteractionType(source.getInteractionType());
        copy.setReason(source.getReason());
        copy.setPayload(copyNullableMap(source.getPayload()));
        return copy;
    }
}
