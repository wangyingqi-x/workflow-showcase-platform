package com.itao.workflowEngine.callback.event;

import com.itao.workflowEngine.nodes.BaseNode;

import java.util.Map;

public class NodeEndData {

    private String uniqueId;
    private String nodeId;
    private String name;
    private String reason;
    private Double runTime;
    private Map<String, Object> result;
    private BaseNode baseNode;

    public static Builder builder() {
        return new Builder();
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Double getRunTime() {
        return runTime;
    }

    public void setRunTime(Double runTime) {
        this.runTime = runTime;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public BaseNode getBaseNode() {
        return baseNode;
    }

    public void setBaseNode(BaseNode baseNode) {
        this.baseNode = baseNode;
    }

    public static final class Builder {
        private final NodeEndData instance = new NodeEndData();

        public Builder uniqueId(String uniqueId) {
            instance.setUniqueId(uniqueId);
            return this;
        }

        public Builder nodeId(String nodeId) {
            instance.setNodeId(nodeId);
            return this;
        }

        public Builder name(String name) {
            instance.setName(name);
            return this;
        }

        public Builder reason(String reason) {
            instance.setReason(reason);
            return this;
        }

        public Builder runTime(Double runTime) {
            instance.setRunTime(runTime);
            return this;
        }

        public Builder baseNode(BaseNode baseNode) {
            instance.setBaseNode(baseNode);
            return this;
        }

        public NodeEndData build() {
            return instance;
        }
    }
}
