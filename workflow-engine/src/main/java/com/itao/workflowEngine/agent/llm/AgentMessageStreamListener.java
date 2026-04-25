package com.itao.workflowEngine.agent.llm;

public interface AgentMessageStreamListener {

    default void onStart(String nodeId, String stage) {
    }

    default void onDelta(String nodeId, String delta) {
    }

    default void onComplete(String nodeId, String fullMessage) {
    }
}
