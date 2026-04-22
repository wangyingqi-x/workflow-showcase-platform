package com.iyunwen.workflowEngine.graph;

import com.iyunwen.workflowEngine.callback.BaseCallback;
import com.iyunwen.workflowEngine.common.BaseNodeData;
import com.iyunwen.workflowEngine.common.NodeGroupParam;
import com.iyunwen.workflowEngine.common.NodeParam;
import com.iyunwen.workflowEngine.common.bo.NodeDataBO;
import com.iyunwen.workflowEngine.common.dto.FlowWsDTO;
import com.iyunwen.workflowEngine.edges.EdgeManage;

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
        return new GraphEngine(userId, asyncMode, flowDTO, edgeManage, nodeDataMap, callback);
    }
}
