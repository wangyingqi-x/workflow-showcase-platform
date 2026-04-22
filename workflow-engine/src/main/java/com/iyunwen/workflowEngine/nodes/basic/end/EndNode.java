package com.iyunwen.workflowEngine.nodes.basic.end;

import com.iyunwen.util.YWStringUtils;
import com.iyunwen.workflowEngine.callback.BaseCallback;
import com.iyunwen.workflowEngine.common.BaseNodeData;
import com.iyunwen.workflowEngine.common.enums.NodeGroupParamKeyEnum;
import com.iyunwen.workflowEngine.common.enums.NodeGroupParamValueTypeEnum;
import com.iyunwen.workflowEngine.edges.EdgeBase;
import com.iyunwen.workflowEngine.graph.GraphState;
import com.iyunwen.workflowEngine.nodes.BaseNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EndNode extends BaseNode {

    public EndNode(BaseNodeData nodeData, String workflowId, String userId, GraphState graphState,
                   List<EdgeBase> targetEdges, List<EdgeBase> sourceEdges, int maxSteps, BaseCallback callback) {
        super(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
    }

    @Override
    public Map<String, Object> _run(String uniqueId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Object outputVariables = nodeParams.get(NodeGroupParamKeyEnum.OUTPUT_VARIABLE.getKey());
        if (outputVariables instanceof List<?> mappings) {
            for (Object mapping : mappings) {
                if (!(mapping instanceof Map<?, ?> raw)) {
                    continue;
                }
                String key = String.valueOf(raw.get("key"));
                if (YWStringUtils.isBlank(key)) {
                    continue;
                }
                Object value = raw.get("value");
                Object typeValue = raw.containsKey("type")
                        ? raw.get("type")
                        : NodeGroupParamValueTypeEnum.NODE_PARAM_VALUE_REF.getType();
                String type = String.valueOf(typeValue);
                if (NodeGroupParamValueTypeEnum.NODE_PARAM_VALUE_INPUT.getType().equals(type)) {
                    result.put(key, value);
                } else {
                    Object referenced = value == null ? null : getOtherNodeVariable(String.valueOf(value));
                    result.put(key, referenced == null ? value : referenced);
                }
            }
        }
        result.putIfAbsent("workflow_status", "completed");
        return result;
    }
}
