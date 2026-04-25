package com.itao.workflowEngine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "workflow")
public class WorkflowConfig {

    private int maxSteps = 128;
    private boolean asyncEnabled = true;
    private GraphEngineConfig graphEngine = new GraphEngineConfig();

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }

    public GraphEngineConfig getGraphEngine() {
        return graphEngine;
    }

    public void setGraphEngine(GraphEngineConfig graphEngine) {
        this.graphEngine = graphEngine;
    }

    public static class GraphEngineConfig {
        private int nodeMaxSteps = 16;
        private int corePoolSize = 4;
        private int maxPoolSize = 8;
        private int queueSize = 32;

        public int getNodeMaxSteps() {
            return nodeMaxSteps;
        }

        public void setNodeMaxSteps(int nodeMaxSteps) {
            this.nodeMaxSteps = nodeMaxSteps;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public void setQueueSize(int queueSize) {
            this.queueSize = queueSize;
        }
    }
}
