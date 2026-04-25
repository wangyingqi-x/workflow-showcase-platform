package com.itao.workflowEngine.checkpoint;

import com.itao.workflowEngine.common.dto.FlowWsDTO;
import com.itao.workflowEngine.graph.model.InteractionContext;
import com.itao.workflowEngine.graph.model.enums.GraphEngineStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraphCheckpointSnapshot {

    private String checkpointId;
    private String runId;
    private String workflowId;
    private String userId;
    private boolean asyncMode;
    private FlowWsDTO workflow;
    private List<String> pendingNodeIds = new ArrayList<>();
    private int doCount;
    private GraphEngineStatus status;
    private String reason;
    private Map<String, Map<String, Object>> nodeVariables = new LinkedHashMap<>();
    private List<Map<String, String>> chatContext = new ArrayList<>();
    private List<Map<String, Object>> agentTrace = new ArrayList<>();
    private List<Map<String, Object>> toolCalls = new ArrayList<>();
    private Map<String, List<Map<String, Object>>> pendingToolCalls = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> toolResults = new LinkedHashMap<>();
    private Map<String, List<String>> nodeSignals = new LinkedHashMap<>();
    private List<String> scheduledNodeIds = new ArrayList<>();
    private InteractionContext interactionContext;

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isAsyncMode() {
        return asyncMode;
    }

    public void setAsyncMode(boolean asyncMode) {
        this.asyncMode = asyncMode;
    }

    public FlowWsDTO getWorkflow() {
        return workflow;
    }

    public void setWorkflow(FlowWsDTO workflow) {
        this.workflow = workflow;
    }

    public List<String> getPendingNodeIds() {
        return pendingNodeIds;
    }

    public void setPendingNodeIds(List<String> pendingNodeIds) {
        this.pendingNodeIds = pendingNodeIds == null ? new ArrayList<>() : new ArrayList<>(pendingNodeIds);
    }

    public int getDoCount() {
        return doCount;
    }

    public void setDoCount(int doCount) {
        this.doCount = doCount;
    }

    public GraphEngineStatus getStatus() {
        return status;
    }

    public void setStatus(GraphEngineStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Map<String, Map<String, Object>> getNodeVariables() {
        return nodeVariables;
    }

    public void setNodeVariables(Map<String, Map<String, Object>> nodeVariables) {
        this.nodeVariables = nodeVariables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(nodeVariables);
    }

    public List<Map<String, String>> getChatContext() {
        return chatContext;
    }

    public void setChatContext(List<Map<String, String>> chatContext) {
        this.chatContext = chatContext == null ? new ArrayList<>() : new ArrayList<>(chatContext);
    }

    public List<Map<String, Object>> getAgentTrace() {
        return agentTrace;
    }

    public void setAgentTrace(List<Map<String, Object>> agentTrace) {
        this.agentTrace = agentTrace == null ? new ArrayList<>() : new ArrayList<>(agentTrace);
    }

    public List<Map<String, Object>> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<Map<String, Object>> toolCalls) {
        this.toolCalls = toolCalls == null ? new ArrayList<>() : new ArrayList<>(toolCalls);
    }

    public Map<String, List<Map<String, Object>>> getPendingToolCalls() {
        return pendingToolCalls;
    }

    public void setPendingToolCalls(Map<String, List<Map<String, Object>>> pendingToolCalls) {
        this.pendingToolCalls = pendingToolCalls == null ? new LinkedHashMap<>() : new LinkedHashMap<>(pendingToolCalls);
    }

    public Map<String, Map<String, Object>> getToolResults() {
        return toolResults;
    }

    public void setToolResults(Map<String, Map<String, Object>> toolResults) {
        this.toolResults = toolResults == null ? new LinkedHashMap<>() : new LinkedHashMap<>(toolResults);
    }

    public Map<String, List<String>> getNodeSignals() {
        return nodeSignals;
    }

    public void setNodeSignals(Map<String, List<String>> nodeSignals) {
        this.nodeSignals = nodeSignals == null ? new LinkedHashMap<>() : new LinkedHashMap<>(nodeSignals);
    }

    public List<String> getScheduledNodeIds() {
        return scheduledNodeIds;
    }

    public void setScheduledNodeIds(List<String> scheduledNodeIds) {
        this.scheduledNodeIds = scheduledNodeIds == null ? new ArrayList<>() : new ArrayList<>(scheduledNodeIds);
    }

    public InteractionContext getInteractionContext() {
        return interactionContext;
    }

    public void setInteractionContext(InteractionContext interactionContext) {
        this.interactionContext = interactionContext;
    }
}
