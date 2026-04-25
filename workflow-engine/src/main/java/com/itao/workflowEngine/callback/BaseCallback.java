package com.itao.workflowEngine.callback;

import com.itao.pojo.workflow.ChatResponse;
import com.itao.workflowEngine.callback.event.NodeEndData;
import com.itao.workflowEngine.callback.event.NodeStartData;
import com.itao.workflowEngine.graph.model.enums.GraphEngineStatus;
import com.itao.workflowEngine.nodes.BaseNode;

import java.util.List;

public interface BaseCallback {

    default void onWorkflowStart(String workflowId) {
    }

    default void onWorkflowEnd(String workflowId, GraphEngineStatus status, String reason) {
    }

    default void onWorkflowError(String workflowId, String error) {
    }

    default void onNodeStart(NodeStartData data) {
    }

    default void onNodeEnd(NodeEndData data) {
    }

    default void onNodeError(BaseNode node, String error) {
    }

    default void onGuideWord(String nodeId, String execId, String guideWord) {
    }

    default void onGuideQuestion(String nodeId, String execId, List<String> guideQuestions) {
    }

    default void onOutputMsg(String nodeId, String execId, String message, String outputKey) {
    }

    default List<ChatResponse> getNodeLogs() {
        return List.of();
    }

    default String getChatId() {
        return "demo-chat";
    }

    default Long getWebId() {
        return 0L;
    }

    default String getAccessToken() {
        return "demo-access-token";
    }
}
