package com.itao.workflowEngine.graph.model;

public class GraphEngineMonitor {

    private long createTime = System.currentTimeMillis();
    private long lastCloneTime = System.currentTimeMillis();
    private long startRunTime = System.currentTimeMillis();
    private int doCount;

    public long getCreateTime() {
        return createTime;
    }

    public long getLastCloneTime() {
        return lastCloneTime;
    }

    public void setLastCloneTime(long lastCloneTime) {
        this.lastCloneTime = lastCloneTime;
    }

    public long getStartRunTime() {
        return startRunTime;
    }

    public void setStartRunTime(long startRunTime) {
        this.startRunTime = startRunTime;
    }

    public int getDoCount() {
        return doCount;
    }

    public void setDoCount(int doCount) {
        this.doCount = doCount;
    }

    public void incrementDoCount() {
        this.doCount++;
    }
}
