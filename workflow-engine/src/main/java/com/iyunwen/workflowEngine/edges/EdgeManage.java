package com.iyunwen.workflowEngine.edges;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EdgeManage {

    private final Map<String, List<EdgeBase>> sourceEdgeMap = new HashMap<>();
    private final Map<String, List<EdgeBase>> targetEdgeMap = new HashMap<>();

    public static EdgeManage fromEdges(List<EdgeBase> edges) {
        EdgeManage manage = new EdgeManage();
        for (EdgeBase edge : edges) {
            manage.sourceEdgeMap.computeIfAbsent(edge.getSourceId(), key -> new ArrayList<>()).add(edge);
            manage.targetEdgeMap.computeIfAbsent(edge.getTargetId(), key -> new ArrayList<>()).add(edge);
        }
        return manage;
    }

    public List<EdgeBase> getTargetEdges(String nodeId) {
        return new ArrayList<>(sourceEdgeMap.getOrDefault(nodeId, List.of()));
    }

    public List<EdgeBase> getSourceEdges(String nodeId) {
        return new ArrayList<>(targetEdgeMap.getOrDefault(nodeId, List.of()));
    }
}
