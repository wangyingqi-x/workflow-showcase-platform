package com.itao.workflowEngine.agent.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolExecutionResult {

    private boolean success;
    private String content;
    private String errorMessage;
    private Map<String, Object> structuredData = new LinkedHashMap<>();

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Map<String, Object> getStructuredData() {
        return structuredData;
    }

    public void setStructuredData(Map<String, Object> structuredData) {
        this.structuredData = structuredData == null ? new LinkedHashMap<>() : new LinkedHashMap<>(structuredData);
    }
}
