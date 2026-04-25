package com.itao.workflowEngine.nodes.basic.polymerize;

import com.itao.workflowEngine.callback.BaseCallback;
import com.itao.workflowEngine.common.BaseNodeData;
import com.itao.workflowEngine.edges.EdgeBase;
import com.itao.workflowEngine.graph.GraphState;
import com.itao.workflowEngine.nodes.BaseNode;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RunWaitingNode extends BaseNode {

    private final Set<String> expectedSources = new HashSet<>();
    private final Map<String, Integer> consumedScheduleCounts = new LinkedHashMap<>();

    public RunWaitingNode(BaseNodeData nodeData, String workflowId, String userId, GraphState graphState,
                          List<EdgeBase> targetEdges, List<EdgeBase> sourceEdges, int maxSteps, BaseCallback callback) {
        super(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
        for (EdgeBase edge : sourceEdges) {
            expectedSources.add(edge.getSourceId());
        }
    }

    @Override
    public boolean canContinueExecution() {
        Set<String> observedSources = new HashSet<>(graphState.getNodeSignals(id));
        Set<String> activeExpectedSources = resolveActiveExpectedSources();
        return !activeExpectedSources.isEmpty() && observedSources.containsAll(activeExpectedSources);
    }

    @Override
    public Map<String, Object> _run(String uniqueId) {
        Set<String> observedSources = new HashSet<>(graphState.getNodeSignals(id));
        Set<String> activeExpectedSources = resolveActiveExpectedSources();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("joined_sources", observedSources.size());
        result.put("expected_sources", activeExpectedSources.size());
        for (String sourceId : activeExpectedSources) {
            consumedScheduleCounts.put(sourceId, graphState.getNodeScheduleCount(sourceId));
        }
        graphState.clearNodeSignals(id);
        return result;
    }

    private Set<String> resolveActiveExpectedSources() {
        Set<String> activeExpectedSources = new HashSet<>();
        for (String sourceId : expectedSources) {
            int currentCount = graphState.getNodeScheduleCount(sourceId);
            int consumedCount = consumedScheduleCounts.getOrDefault(sourceId, 0);
            if (currentCount > consumedCount) {
                activeExpectedSources.add(sourceId);
            }
        }
        return activeExpectedSources;
    }
}
