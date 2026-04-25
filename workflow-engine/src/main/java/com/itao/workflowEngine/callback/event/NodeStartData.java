package com.itao.workflowEngine.callback.event;

import com.itao.workflowEngine.nodes.BaseNode;

public class NodeStartData {

    private String uniqueId;
    private String nodeId;
    private String name;
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

    public BaseNode getBaseNode() {
        return baseNode;
    }

    public void setBaseNode(BaseNode baseNode) {
        this.baseNode = baseNode;
    }

    public static final class Builder {
        private final NodeStartData instance = new NodeStartData();

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

        public Builder baseNode(BaseNode baseNode) {
            instance.setBaseNode(baseNode);
            return this;
        }

        public NodeStartData build() {
            return instance;
        }
    }
}
