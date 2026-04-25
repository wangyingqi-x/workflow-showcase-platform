package com.itao.demo.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DemoWorkflowRequest {

    private String workflowId;
    private String workflowName;
    private String description;
    private String userMessage;
    private Boolean asyncMode = Boolean.TRUE;
    private List<ConversationMessage> conversation = new ArrayList<>();
    private List<DemoNodeConfig> nodes = new ArrayList<>();
    private List<DemoEdgeConfig> edges = new ArrayList<>();

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public Boolean getAsyncMode() {
        return asyncMode;
    }

    public void setAsyncMode(Boolean asyncMode) {
        this.asyncMode = asyncMode;
    }

    public List<ConversationMessage> getConversation() {
        return conversation;
    }

    public void setConversation(List<ConversationMessage> conversation) {
        this.conversation = conversation == null ? new ArrayList<>() : new ArrayList<>(conversation);
    }

    public List<DemoNodeConfig> getNodes() {
        return nodes;
    }

    public void setNodes(List<DemoNodeConfig> nodes) {
        this.nodes = nodes;
    }

    public List<DemoEdgeConfig> getEdges() {
        return edges;
    }

    public void setEdges(List<DemoEdgeConfig> edges) {
        this.edges = edges;
    }

    public static class DemoNodeConfig {
        private String id;
        private String type;
        private String name;
        private String description;
        private String guideWord;
        private List<String> guideQuestions = new ArrayList<>();
        private String message;
        private Integer delayMs;
        private Map<String, Object> params = new LinkedHashMap<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getGuideWord() {
            return guideWord;
        }

        public void setGuideWord(String guideWord) {
            this.guideWord = guideWord;
        }

        public List<String> getGuideQuestions() {
            return guideQuestions;
        }

        public void setGuideQuestions(List<String> guideQuestions) {
            this.guideQuestions = guideQuestions;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Integer getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(Integer delayMs) {
            this.delayMs = delayMs;
        }

        public Map<String, Object> getParams() {
            return params;
        }

        public void setParams(Map<String, Object> params) {
            this.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        }
    }

    public static class ConversationMessage {
        private String role;
        private String message;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static class DemoEdgeConfig {
        private String source;
        private String target;
        private String sourceHandle;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getSourceHandle() {
            return sourceHandle;
        }

        public void setSourceHandle(String sourceHandle) {
            this.sourceHandle = sourceHandle;
        }
    }
}
