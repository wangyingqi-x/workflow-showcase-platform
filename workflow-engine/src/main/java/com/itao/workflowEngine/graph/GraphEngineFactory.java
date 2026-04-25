package com.itao.workflowEngine.graph;

import com.itao.workflowEngine.callback.BaseCallback;
import com.itao.workflowEngine.checkpoint.GraphCheckpointSnapshot;
import com.itao.workflowEngine.checkpoint.GraphExecutionOptions;
import com.itao.workflowEngine.common.BaseNodeData;
import com.itao.workflowEngine.common.NodeGroupParam;
import com.itao.workflowEngine.common.NodeParam;
import com.itao.workflowEngine.common.bo.NodeDataBO;
import com.itao.workflowEngine.common.dto.FlowWsDTO;
import com.itao.workflowEngine.edges.EdgeManage;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GraphEngineFactory {

    private static final GraphEngineFactory INSTANCE = new GraphEngineFactory();

    private GraphEngineFactory() {
    }

    public static GraphEngineFactory getInstance() {
        return INSTANCE;
    }

    public GraphEngine getNewEngineInstanceByData(String userId,
                                                  FlowWsDTO flowDTO,
                                                  boolean asyncMode,
                                                  BaseCallback callback) {
        return getNewEngineInstanceByData(userId, flowDTO, asyncMode, callback, new GraphExecutionOptions());
    }

    public GraphEngine getNewEngineInstanceByData(String userId,
                                                  FlowWsDTO flowDTO,
                                                  boolean asyncMode,
                                                  BaseCallback callback,
                                                  GraphExecutionOptions executionOptions) {
        EdgeManage edgeManage = EdgeManage.fromEdges(flowDTO.getEdges());
        Map<String, BaseNodeData> nodeDataMap = new LinkedHashMap<>();
        for (NodeDataBO nodeDataBO : flowDTO.getNodes()) {
            BaseNodeData nodeData = nodeDataBO.getData();
            if (nodeData == null) {
                continue;
            }
            Map<String, NodeParam> flattenedParams = new LinkedHashMap<>();
            if (nodeData.getGroupParams() != null) {
                for (NodeGroupParam groupParam : nodeData.getGroupParams()) {
                    if (groupParam.getParams() == null) {
                        continue;
                    }
                    for (NodeParam nodeParam : groupParam.getParams()) {
                        flattenedParams.put(nodeParam.getKey(), nodeParam);
                    }
                }
            }
            nodeData.setParams(flattenedParams);
            nodeDataMap.put(nodeData.getId(), nodeData);
        }
        return new GraphEngine(userId, asyncMode, flowDTO, edgeManage, nodeDataMap, callback, executionOptions, null);
    }

    public GraphEngine getNewEngineInstanceByCheckpoint(GraphCheckpointSnapshot checkpointSnapshot,
                                                        BaseCallback callback) {
        if (checkpointSnapshot == null) {
            throw new IllegalArgumentException("Checkpoint snapshot cannot be null.");
        }
        GraphExecutionOptions options = new GraphExecutionOptions();
        options.setRunId(checkpointSnapshot.getRunId());
        return getNewEngineInstanceByData(
                checkpointSnapshot.getUserId(),
                checkpointSnapshot.getWorkflow(),
                checkpointSnapshot.isAsyncMode(),
                callback,
                options,
                checkpointSnapshot
        );
    }

    private GraphEngine getNewEngineInstanceByData(String userId,
                                                   FlowWsDTO flowDTO,
                                                   boolean asyncMode,
                                                   BaseCallback callback,
                                                   GraphExecutionOptions executionOptions,
                                                   GraphCheckpointSnapshot checkpointSnapshot) {
        EdgeManage edgeManage = EdgeManage.fromEdges(flowDTO.getEdges());
        Map<String, BaseNodeData> nodeDataMap = new LinkedHashMap<>();
        for (NodeDataBO nodeDataBO : flowDTO.getNodes()) {
            BaseNodeData nodeData = nodeDataBO.getData();
            if (nodeData == null) {
                continue;
            }
            Map<String, NodeParam> flattenedParams = new LinkedHashMap<>();
            if (nodeData.getGroupParams() != null) {
                for (NodeGroupParam groupParam : nodeData.getGroupParams()) {
                    if (groupParam.getParams() == null) {
                        continue;
                    }
                    for (NodeParam nodeParam : groupParam.getParams()) {
                        flattenedParams.put(nodeParam.getKey(), nodeParam);
                    }
                }
            }
            nodeData.setParams(flattenedParams);
            nodeDataMap.put(nodeData.getId(), nodeData);
        }
        return new GraphEngine(userId, asyncMode, flowDTO, edgeManage, nodeDataMap, callback, executionOptions, checkpointSnapshot);
    }
}
