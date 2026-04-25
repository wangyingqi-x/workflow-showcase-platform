package com.itao.workflowEngine.graph.model;

import com.itao.workflowEngine.graph.model.enums.NodeRunStatus;

public class NodeRunResult {

    private final String nodeId;
    private final NodeRunStatus status;
    private final String reason;

    public NodeRunResult(String nodeId, NodeRunStatus status, String reason) {
        this.nodeId = nodeId;
        this.status = status;
        this.reason = reason;
    }

    public String getNodeId() {
        return nodeId;
    }

    public NodeRunStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}
