package com.iyunwen.demo.callback;

import com.iyunwen.pojo.workflow.ChatResponse;
import com.iyunwen.workflowEngine.callback.BaseCallback;
import com.iyunwen.workflowEngine.callback.event.NodeEndData;
import com.iyunwen.workflowEngine.callback.event.NodeStartData;
import com.iyunwen.workflowEngine.graph.model.enums.GraphEngineStatus;
import com.iyunwen.workflowEngine.nodes.BaseNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryDemoCallback implements BaseCallback {

    private final List<ChatResponse> nodeLogs = new ArrayList<>();

    @Override
    public void onWorkflowStart(String workflowId) {
        add("workflow_start", workflowId, "Workflow started", Map.of("workflowId", workflowId));
    }

    @Override
    public void onWorkflowEnd(String workflowId, GraphEngineStatus status, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflowId", workflowId);
        payload.put("status", status.name());
        payload.put("reason", reason);
        add("workflow_end", workflowId, "Workflow finished", payload);
    }

    @Override
    public void onWorkflowError(String workflowId, String error) {
        add("workflow_error", workflowId, error, Map.of("workflowId", workflowId));
    }

    @Override
    public void onNodeStart(NodeStartData data) {
        add("node_start", data.getNodeId(), data.getName(), Map.of(
                "thread", Thread.currentThread().getName(),
                "type", data.getBaseNode().getType()
        ));
    }

    @Override
    public void onNodeEnd(NodeEndData data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("thread", Thread.currentThread().getName());
        payload.put("runTimeMs", data.getRunTime());
        payload.put("result", data.getResult());
        payload.put("reason", data.getReason());
        add("node_end", data.getNodeId(), data.getName(), payload);
    }

    @Override
    public void onNodeError(BaseNode node, String error) {
        add("node_error", node.getId(), error, Map.of("type", node.getType()));
    }

    @Override
    public void onGuideWord(String nodeId, String execId, String guideWord) {
        add("guide_word", nodeId, guideWord, Map.of("execId", execId));
    }

    @Override
    public void onGuideQuestion(String nodeId, String execId, List<String> guideQuestions) {
        add("guide_questions", nodeId, "Guide questions emitted", Map.of("questions", guideQuestions, "execId", execId));
    }

    @Override
    public void onOutputMsg(String nodeId, String execId, String message, String outputKey) {
        add("output", nodeId, message, Map.of("execId", execId, "outputKey", outputKey));
    }

    @Override
    public List<ChatResponse> getNodeLogs() {
        return nodeLogs;
    }

    private void add(String eventType, String nodeId, String message, Map<String, Object> payload) {
        ChatResponse response = new ChatResponse();
        response.setEventType(eventType);
        response.setNodeId(nodeId);
        response.setNodeName(nodeId);
        response.setMessage(message);
        response.setPayload(payload);
        response.setTimestamp(Instant.now());
        nodeLogs.add(response);
    }
}
