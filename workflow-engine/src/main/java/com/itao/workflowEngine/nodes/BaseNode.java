package com.itao.workflowEngine.nodes;

import com.itao.pojo.workflow.ChatResponse;
import com.itao.util.MessageUtils;
import com.itao.workflowEngine.callback.BaseCallback;
import com.itao.workflowEngine.callback.event.NodeEndData;
import com.itao.workflowEngine.callback.event.NodeStartData;
import com.itao.workflowEngine.common.BaseNodeData;
import com.itao.workflowEngine.common.NodeParam;
import com.itao.workflowEngine.common.enums.NodeInteractiveType;
import com.itao.workflowEngine.edges.EdgeBase;
import com.itao.workflowEngine.graph.GraphState;
import com.itao.workflowEngine.graph.model.InteractionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class BaseNode {

    protected final String id;
    protected final String type;
    protected final String name;
    protected final String workflowId;
    protected final String userId;
    protected final GraphState graphState;
    protected final BaseNodeData nodeData;
    protected final Map<String, Object> nodeParams = new LinkedHashMap<>();
    protected final List<EdgeBase> targetEdges;
    protected final List<EdgeBase> sourceEdges;
    protected final int maxSteps;
    protected final BaseCallback callback;

    protected int currentStep;
    protected boolean stopFlag;
    protected String runSourceNodeId;

    protected BaseNode(BaseNodeData nodeData,
                       String workflowId,
                       String userId,
                       GraphState graphState,
                       List<EdgeBase> targetEdges,
                       List<EdgeBase> sourceEdges,
                       int maxSteps,
                       BaseCallback callback) {
        this.id = nodeData.getId();
        this.type = nodeData.getType();
        this.name = nodeData.getName();
        this.workflowId = workflowId;
        this.userId = userId;
        this.graphState = graphState;
        this.nodeData = nodeData;
        this.targetEdges = targetEdges == null ? List.of() : targetEdges;
        this.sourceEdges = sourceEdges == null ? List.of() : sourceEdges;
        this.maxSteps = maxSteps;
        this.callback = callback;
        initData();
    }

    protected void initData() {
        if (nodeData.getParams() != null) {
            nodeData.getParams().values().forEach(param -> {
                nodeParams.put(param.getKey(), param.getValue());
                if (param.getChildren() != null) {
                    for (NodeParam child : param.getChildren()) {
                        nodeParams.put(child.getKey(), child.getValue());
                    }
                }
            });
        }
    }

    public abstract Map<String, Object> _run(String uniqueId);

    public Map<String, Object> run(Map<String, Object> state) {
        if (stopFlag) {
            throw new IllegalStateException(MessageUtils.getMsg("BaseNode.run.stop.user"));
        }
        if (currentStep >= maxSteps) {
            throw new IllegalStateException(MessageUtils.getMsg("BaseNode.run.over.maxStep", new Object[]{name, maxSteps}));
        }

        String uniqueId = UUID.randomUUID().toString().replace("-", "");
        long startTime = System.nanoTime();

        if (callback != null) {
            callback.onNodeStart(NodeStartData.builder()
                    .uniqueId(uniqueId)
                    .nodeId(id)
                    .name(name)
                    .baseNode(this)
                    .build());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        String reason = null;
        try {
            result = _run(uniqueId);
            if (result != null) {
                for (Map.Entry<String, Object> entry : result.entrySet()) {
                    graphState.setVariable(id, entry.getKey(), entry.getValue());
                }
            }
            currentStep++;
            return result;
        } catch (RuntimeException ex) {
            reason = ex.getMessage();
            if (callback != null) {
                callback.onNodeError(this, reason);
            }
            throw ex;
        } finally {
            if (callback != null) {
                NodeEndData endData = NodeEndData.builder()
                        .uniqueId(uniqueId)
                        .nodeId(id)
                        .name(name)
                        .reason(reason)
                        .runTime((System.nanoTime() - startTime) / 1_000_000D)
                        .baseNode(this)
                        .build();
                endData.setResult(result);
                callback.onNodeEnd(endData);
            }
        }
    }

    public List<String> routeNode() {
        return targetEdges.stream()
                .map(EdgeBase::getTargetId)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public void setRunSourceNodeId(String runSourceNodeId) {
        this.runSourceNodeId = runSourceNodeId;
        graphState.recordNodeSignal(id, runSourceNodeId);
    }

    public NodeInteractiveType getInteractiveType() {
        return NodeInteractiveType.NOINTERACTIVE;
    }

    public boolean canContinueExecution() {
        return true;
    }

    public boolean needsInteraction() {
        return false;
    }

    public boolean shouldPauseAfterRun() {
        return false;
    }

    public InteractionContext buildInteractionContext() {
        return null;
    }

    public void handleInteractionResult(Map<String, Object> interactionData) {
    }

    public void stop() {
        this.stopFlag = true;
    }

    public List<ChatResponse> getNodeLogs() {
        return callback == null ? List.of() : callback.getNodeLogs();
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public GraphState getGraphState() {
        return graphState;
    }

    public BaseCallback getCallback() {
        return callback;
    }

    public Object getOtherNodeVariable(String variablePath) {
        return graphState.getVariableByStr(variablePath);
    }
}
