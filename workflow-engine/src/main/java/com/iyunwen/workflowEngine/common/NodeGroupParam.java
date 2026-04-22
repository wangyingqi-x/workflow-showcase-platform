package com.iyunwen.workflowEngine.common;

import java.util.ArrayList;
import java.util.List;

public class NodeGroupParam {

    private String name;
    private List<NodeParam> params = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<NodeParam> getParams() {
        return params;
    }

    public void setParams(List<NodeParam> params) {
        this.params = params;
    }
}
