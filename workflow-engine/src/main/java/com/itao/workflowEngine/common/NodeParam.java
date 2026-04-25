package com.itao.workflowEngine.common;

import java.util.ArrayList;
import java.util.List;

public class NodeParam {

    private String key;
    private Object value;
    private List<NodeParamOptions> options = new ArrayList<>();
    private List<NodeParam> children = new ArrayList<>();

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public List<NodeParamOptions> getOptions() {
        return options;
    }

    public void setOptions(List<NodeParamOptions> options) {
        this.options = options;
    }

    public List<NodeParam> getChildren() {
        return children;
    }

    public void setChildren(List<NodeParam> children) {
        this.children = children;
    }
}
