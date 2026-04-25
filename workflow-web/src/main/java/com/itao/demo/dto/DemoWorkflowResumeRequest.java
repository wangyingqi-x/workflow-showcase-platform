package com.itao.demo.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class DemoWorkflowResumeRequest {

    private String checkpointId;
    private Map<String, Object> interactionData = new LinkedHashMap<>();

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }

    public Map<String, Object> getInteractionData() {
        return interactionData;
    }

    public void setInteractionData(Map<String, Object> interactionData) {
        this.interactionData = interactionData == null ? new LinkedHashMap<>() : new LinkedHashMap<>(interactionData);
    }
}
