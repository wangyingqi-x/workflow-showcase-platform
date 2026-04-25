package com.itao.workflowEngine.checkpoint;

import java.util.LinkedHashSet;
import java.util.Set;

public class GraphExecutionOptions {

    private String runId;
    private Set<String> interruptBeforeNodeIds = new LinkedHashSet<>();

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public Set<String> getInterruptBeforeNodeIds() {
        return interruptBeforeNodeIds;
    }

    public void setInterruptBeforeNodeIds(Set<String> interruptBeforeNodeIds) {
        this.interruptBeforeNodeIds = interruptBeforeNodeIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(interruptBeforeNodeIds);
    }
}
