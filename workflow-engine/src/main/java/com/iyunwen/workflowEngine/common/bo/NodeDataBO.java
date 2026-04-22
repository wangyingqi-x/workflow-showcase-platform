package com.iyunwen.workflowEngine.common.bo;

import com.iyunwen.workflowEngine.common.BaseNodeData;

public class NodeDataBO {

    private String id;
    private String type;
    private BaseNodeData data;

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

    public BaseNodeData getData() {
        return data;
    }

    public void setData(BaseNodeData data) {
        this.data = data;
    }
}
