package com.itao.workflowEngine.edges;

public class EdgeBase {

    private String id;
    private String source;
    private String target;
    private String sourceHandle;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getSourceId() {
        return source;
    }

    public String getTargetId() {
        return target;
    }
}
