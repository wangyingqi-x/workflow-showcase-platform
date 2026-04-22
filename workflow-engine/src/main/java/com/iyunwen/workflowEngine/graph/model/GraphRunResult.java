package com.iyunwen.workflowEngine.graph.model;

import com.iyunwen.workflowEngine.graph.model.enums.GraphEngineStatus;

public class GraphRunResult {

    private final GraphEngineStatus status;
    private final String reason;

    public GraphRunResult(GraphEngineStatus status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public GraphEngineStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}
