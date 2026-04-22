package com.iyunwen.workflowEngine.common.dto;

import com.iyunwen.pojo.workflow.ViewPort;
import com.iyunwen.workflowEngine.common.bo.NodeDataBO;
import com.iyunwen.workflowEngine.edges.EdgeBase;

import java.util.ArrayList;
import java.util.List;

public class FlowWsDTO {

    private String id;
    private String name;
    private String description;
    private List<NodeDataBO> nodes = new ArrayList<>();
    private List<EdgeBase> edges = new ArrayList<>();
    private ViewPort viewport;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public List<NodeDataBO> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeDataBO> nodes) {
        this.nodes = nodes;
    }

    public List<EdgeBase> getEdges() {
        return edges;
    }

    public void setEdges(List<EdgeBase> edges) {
        this.edges = edges;
    }

    public ViewPort getViewport() {
        return viewport;
    }

    public void setViewport(ViewPort viewport) {
        this.viewport = viewport;
    }
}
