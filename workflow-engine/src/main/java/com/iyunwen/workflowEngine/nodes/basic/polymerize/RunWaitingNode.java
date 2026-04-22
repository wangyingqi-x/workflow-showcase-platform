package com.iyunwen.workflowEngine.nodes.basic.polymerize;

import com.iyunwen.workflowEngine.callback.BaseCallback;
import com.iyunwen.workflowEngine.common.BaseNodeData;
import com.iyunwen.workflowEngine.edges.EdgeBase;
import com.iyunwen.workflowEngine.graph.GraphState;
import com.iyunwen.workflowEngine.nodes.BaseNode;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RunWaitingNode extends BaseNode {

    private final Set<String> expectedSources = new HashSet<>();
    private final Set<String> observedSources = new HashSet<>();

    public RunWaitingNode(BaseNodeData nodeData, String workflowId, String userId, GraphState graphState,
                          List<EdgeBase> targetEdges, List<EdgeBase> sourceEdges, int maxSteps, BaseCallback callback) {
        super(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
        for (EdgeBase edge : sourceEdges) {
            expectedSources.add(edge.getSourceId());
        }
    }

    @Override
    public void setRunSourceNodeId(String runSourceNodeId) {
        if (runSourceNodeId != null) {
            observedSources.add(runSourceNodeId);
        }
    }

    @Override
    public boolean canContinueExecution() {
        return observedSources.containsAll(expectedSources);
    }

    @Override
    public Map<String, Object> _run(String uniqueId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("joined_sources", observedSources.size());
        observedSources.clear();
        return result;
    }
}
