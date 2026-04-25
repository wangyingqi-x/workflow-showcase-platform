package com.itao.workflowEngine.agent.model;

import com.itao.workflowEngine.agent.llm.AgentMessageStreamListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentPromptContext {

    private String workflowId;
    private String nodeId;
    private String userId;
    private Map<String, Object> nodeParams = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> variables = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> toolResults = new LinkedHashMap<>();
    private List<Map<String, String>> chatContext = List.of();
    private AgentMessageStreamListener streamListener;

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Map<String, Object> getNodeParams() {
        return nodeParams;
    }

    public void setNodeParams(Map<String, Object> nodeParams) {
        this.nodeParams = nodeParams == null ? new LinkedHashMap<>() : new LinkedHashMap<>(nodeParams);
    }

    public Map<String, Map<String, Object>> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Map<String, Object>> variables) {
        this.variables = variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables);
    }

    public Map<String, Map<String, Object>> getToolResults() {
        return toolResults;
    }

    public void setToolResults(Map<String, Map<String, Object>> toolResults) {
        this.toolResults = toolResults == null ? new LinkedHashMap<>() : new LinkedHashMap<>(toolResults);
    }

    public List<Map<String, String>> getChatContext() {
        return chatContext;
    }

    public void setChatContext(List<Map<String, String>> chatContext) {
        this.chatContext = chatContext == null ? List.of() : List.copyOf(chatContext);
    }

    public AgentMessageStreamListener getStreamListener() {
        return streamListener;
    }

    public void setStreamListener(AgentMessageStreamListener streamListener) {
        this.streamListener = streamListener;
    }
}
