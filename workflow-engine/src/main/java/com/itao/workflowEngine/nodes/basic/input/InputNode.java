package com.itao.workflowEngine.nodes.basic.input;

import com.itao.workflowEngine.callback.BaseCallback;
import com.itao.workflowEngine.common.BaseNodeData;
import com.itao.workflowEngine.common.enums.RoleType;
import com.itao.workflowEngine.edges.EdgeBase;
import com.itao.workflowEngine.graph.GraphState;
import com.itao.workflowEngine.graph.model.InteractionContext;
import com.itao.workflowEngine.graph.model.InteractionType;
import com.itao.workflowEngine.nodes.BaseNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InputNode extends BaseNode {

    private static final String DEFAULT_INPUT_KEY = "user_input";
    private static final String INPUT_VALUE_KEY = "input_value";

    public InputNode(BaseNodeData nodeData, String workflowId, String userId, GraphState graphState,
                     List<EdgeBase> targetEdges, List<EdgeBase> sourceEdges, int maxSteps, BaseCallback callback) {
        super(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
    }

    @Override
    public boolean needsInteraction() {
        return graphState.getVariable(id, INPUT_VALUE_KEY) == null;
    }

    @Override
    public InteractionContext buildInteractionContext() {
        InteractionContext interactionContext = new InteractionContext();
        interactionContext.setNodeId(id);
        interactionContext.setNodeName(name);
        interactionContext.setNodeType(type);
        interactionContext.setInteractionType(InteractionType.USER_INPUT);
        interactionContext.setReason(stringParam("prompt", "This step needs more input before the workflow can continue."));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputKey", stringParam("input_key", DEFAULT_INPUT_KEY));
        payload.put("label", stringParam("input_label", "Additional input"));
        payload.put("placeholder", stringParam("placeholder", "Type the extra detail to continue."));
        payload.put("multiline", booleanParam("multiline", true));
        interactionContext.setPayload(payload);
        return interactionContext;
    }

    @Override
    public void handleInteractionResult(Map<String, Object> interactionData) {
        String inputKey = stringParam("input_key", DEFAULT_INPUT_KEY);
        Object rawValue = interactionData == null ? null : interactionData.get(inputKey);
        if (rawValue == null && interactionData != null) {
            rawValue = interactionData.get("value");
        }
        String value = rawValue == null ? "" : String.valueOf(rawValue).trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Input node requires a non-empty value.");
        }
        graphState.setVariable(id, INPUT_VALUE_KEY, value);
        if (booleanParam("save_to_chat_context", true)) {
            graphState.saveContext(value, RoleType.HUMAN);
        }
    }

    @Override
    public Map<String, Object> _run(String uniqueId) {
        String inputKey = stringParam("input_key", DEFAULT_INPUT_KEY);
        Object value = graphState.getVariable(id, INPUT_VALUE_KEY);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(inputKey, value);
        result.put("message", String.valueOf(value));
        return result;
    }

    private boolean booleanParam(String key, boolean defaultValue) {
        Object value = nodeParams.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return defaultValue;
    }

    private String stringParam(String key, String defaultValue) {
        Object value = nodeParams.get(key);
        return value == null || String.valueOf(value).trim().isBlank() ? defaultValue : String.valueOf(value).trim();
    }
}
