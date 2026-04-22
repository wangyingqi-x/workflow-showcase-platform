package com.iyunwen.workflowEngine.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseNodeData {

    private String id;
    private String type;
    private String name;
    private String description;
    @JsonProperty("group_params")
    private List<NodeGroupParam> groupParams;
    private Map<String, NodeParam> params;
    private BaseNodeDataTab tab;

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

    public List<NodeGroupParam> getGroupParams() {
        return groupParams;
    }

    public void setGroupParams(List<NodeGroupParam> groupParams) {
        this.groupParams = groupParams;
    }

    public Map<String, NodeParam> getParams() {
        return params;
    }

    public void setParams(Map<String, NodeParam> params) {
        this.params = params;
    }

    public BaseNodeDataTab getTab() {
        return tab;
    }

    public void setTab(BaseNodeDataTab tab) {
        this.tab = tab;
    }
}
