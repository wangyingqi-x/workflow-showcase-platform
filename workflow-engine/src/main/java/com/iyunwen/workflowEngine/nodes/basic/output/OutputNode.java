package com.iyunwen.workflowEngine.nodes.basic.output;

import com.iyunwen.util.YWMessageUtils;
import com.iyunwen.util.YWObjectToStringUtils;
import com.iyunwen.workflowEngine.callback.BaseCallback;
import com.iyunwen.workflowEngine.common.BaseNodeData;
import com.iyunwen.workflowEngine.common.enums.NodeGroupParamKeyEnum;
import com.iyunwen.workflowEngine.common.enums.RoleType;
import com.iyunwen.workflowEngine.edges.EdgeBase;
import com.iyunwen.workflowEngine.graph.GraphState;
import com.iyunwen.workflowEngine.nodes.BaseNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OutputNode extends BaseNode {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{#([^#]+)#\\}\\}");

    public OutputNode(BaseNodeData nodeData, String workflowId, String userId, GraphState graphState,
                      List<EdgeBase> targetEdges, List<EdgeBase> sourceEdges, int maxSteps, BaseCallback callback) {
        super(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
    }

    @Override
    public Map<String, Object> _run(String uniqueId) {
        String message = String.valueOf(nodeParams.getOrDefault(NodeGroupParamKeyEnum.MESSAGE.getKey(),
                YWMessageUtils.getMsg("OutputNode.message.empty.default")));
        message = replaceVariableReferences(message);

        Object delayValue = nodeParams.get("delay_ms");
        if (delayValue instanceof Number number && number.longValue() > 0) {
            sleep(number.longValue());
        }

        graphState.saveContext(message, RoleType.AI);
        callback.onOutputMsg(id, uniqueId, message, NodeGroupParamKeyEnum.MESSAGE.getKey());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(NodeGroupParamKeyEnum.MESSAGE.getKey(), message);
        result.put("thread_name", Thread.currentThread().getName());
        return result;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Output node interrupted", ex);
        }
    }

    private String replaceVariableReferences(String message) {
        Matcher matcher = VARIABLE_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object value = getOtherNodeVariable(matcher.group(1));
            String replacement = YWObjectToStringUtils.convertObjectToString(value);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
