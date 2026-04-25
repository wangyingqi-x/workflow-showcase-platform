package com.itao.workflowEngine.graph;

import com.itao.pojo.workflow.enums.NodeTypeEnum;
import com.itao.util.MessageUtils;
import com.itao.util.spring.SpringBeanProvider;
import com.itao.workflowEngine.callback.BaseCallback;
import com.itao.workflowEngine.checkpoint.CheckpointStore;
import com.itao.workflowEngine.checkpoint.GraphCheckpointSnapshot;
import com.itao.workflowEngine.checkpoint.GraphExecutionOptions;
import com.itao.workflowEngine.common.BaseNodeData;
import com.itao.workflowEngine.common.dto.FlowWsDTO;
import com.itao.workflowEngine.config.WorkflowConfig;
import com.itao.workflowEngine.edges.EdgeManage;
import com.itao.workflowEngine.graph.model.GraphEngineMonitor;
import com.itao.workflowEngine.graph.model.GraphRunResult;
import com.itao.workflowEngine.graph.model.InteractionContext;
import com.itao.workflowEngine.graph.model.NodeRunResult;
import com.itao.workflowEngine.graph.model.enums.GraphEngineStatus;
import com.itao.workflowEngine.graph.thread.RunGraphNodeTask;
import com.itao.workflowEngine.graph.thread.RunGraphNodeThreadPool;
import com.itao.workflowEngine.nodes.BaseNode;
import com.itao.workflowEngine.nodes.NodeFactory;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class GraphEngine implements AutoCloseable {

    private final String userId;
    private final boolean asyncMode;
    private final FlowWsDTO workflowDTO;
    private final EdgeManage edgeManage;
    private final Map<String, BaseNodeData> allNodeDataMap;
    private final GraphState graphState = new GraphState();
    private final BaseCallback callback;
    private final Deque<String> runQueue = new ArrayDeque<>();
    private final Map<String, BaseNode> nodeInstanceMap = new ConcurrentHashMap<>();
    private final GraphEngineMonitor monitor = new GraphEngineMonitor();
    private final GraphExecutionOptions executionOptions;
    private final int graphMaxSteps;
    private final int nodeMaxSteps;
    private final RunGraphNodeThreadPool threadPool;
    private final CheckpointStore checkpointStore;
    private final String runId;

    private GraphEngineStatus status = GraphEngineStatus.WAITING;
    private String reason = "";
    private String lastCheckpointId = "";

    GraphEngine(String userId,
                boolean asyncMode,
                FlowWsDTO workflowDTO,
                EdgeManage edgeManage,
                Map<String, BaseNodeData> allNodeDataMap,
                BaseCallback callback,
                GraphExecutionOptions executionOptions,
                GraphCheckpointSnapshot checkpointSnapshot) {
        this.userId = userId;
        this.asyncMode = asyncMode;
        this.workflowDTO = workflowDTO;
        this.edgeManage = edgeManage;
        this.allNodeDataMap = allNodeDataMap;
        this.callback = callback;
        this.executionOptions = executionOptions == null ? new GraphExecutionOptions() : executionOptions;
        this.runId = this.executionOptions.getRunId() == null || this.executionOptions.getRunId().isBlank()
                ? "run-" + UUID.randomUUID().toString().substring(0, 8)
                : this.executionOptions.getRunId();

        WorkflowConfig config;
        try {
            config = SpringBeanProvider.getBean(WorkflowConfig.class);
        } catch (Exception ex) {
            config = new WorkflowConfig();
        }
        this.graphMaxSteps = config.getMaxSteps();
        this.nodeMaxSteps = config.getGraphEngine().getNodeMaxSteps();
        this.threadPool = SpringBeanProvider.getBean(RunGraphNodeThreadPool.class);
        this.checkpointStore = SpringBeanProvider.getBean(CheckpointStore.class);
        if (checkpointSnapshot != null) {
            restoreCheckpoint(checkpointSnapshot);
        }
    }

    public GraphRunResult run(Map<String, Object> inputData) {
        if (monitor.getDoCount() == 0 && runQueue.isEmpty()) {
            callback.onWorkflowStart(workflowDTO.getId());
            monitor.setStartRunTime(System.currentTimeMillis());
            applyInitialInput(inputData);
            enqueueStartNode();
        }
        status = GraphEngineStatus.RUNNING;
        reason = "";

        try {
            while (!runQueue.isEmpty() && status == GraphEngineStatus.RUNNING) {
                if (monitor.getDoCount() >= graphMaxSteps) {
                    fail(MessageUtils.getMsg("GraphEngine.max.steps.reached"));
                    break;
                }
                executeCurrentBatch();
            }
            if (status == GraphEngineStatus.RUNNING) {
                status = GraphEngineStatus.COMPLETED;
            }
            callback.onWorkflowEnd(workflowDTO.getId(), status, reason);
            return new GraphRunResult(status, reason);
        } catch (RuntimeException ex) {
            fail(ex.getMessage());
            callback.onWorkflowError(workflowDTO.getId(), reason);
            callback.onWorkflowEnd(workflowDTO.getId(), status, reason);
            return new GraphRunResult(status, reason);
        }
    }

    public GraphRunResult resume(Map<String, Object> interactionData) {
        InteractionContext interactionContext = graphState.getPendingInteraction();
        if (interactionContext == null || interactionContext.getNodeId() == null || interactionContext.getNodeId().isBlank()) {
            throw new IllegalStateException("No pending interaction is available for resume.");
        }

        BaseNode pendingNode = getOrCreateNodeInstance(interactionContext.getNodeId());
        if (pendingNode == null) {
            throw new IllegalStateException("Pending interaction node could not be restored: " + interactionContext.getNodeId());
        }

        pendingNode.handleInteractionResult(interactionData == null ? Map.of() : interactionData);
        graphState.clearPendingInteraction();
        runQueue.addFirst(pendingNode.getId());
        status = GraphEngineStatus.RUNNING;
        reason = "";
        return run(Map.of());
    }

    private void enqueueStartNode() {
        for (Map.Entry<String, BaseNodeData> entry : allNodeDataMap.entrySet()) {
            if (NodeTypeEnum.START.getType().equalsIgnoreCase(entry.getValue().getType())) {
                runQueue.addLast(entry.getKey());
                graphState.markNodeScheduled(entry.getKey());
                return;
            }
        }
        throw new IllegalStateException(MessageUtils.getMsg("Graph.engine.noStartNode"));
    }

    private void executeCurrentBatch() {
        List<String> batchNodeIds = new ArrayList<>();
        while (!runQueue.isEmpty()) {
            batchNodeIds.add(runQueue.removeFirst());
        }

        if (shouldInterruptBefore(batchNodeIds)) {
            requeueBatch(batchNodeIds);
            interrupt("Interrupted before configured node.", batchNodeIds);
            return;
        }

        List<BaseNode> runnableNodes = new ArrayList<>();
        for (int index = 0; index < batchNodeIds.size(); index++) {
            String nodeId = batchNodeIds.get(index);
            BaseNode node = getOrCreateNodeInstance(nodeId);
            if (node == null) {
                continue;
            }
            if (node.needsInteraction()) {
                requeueBatchForInteraction(runnableNodes, batchNodeIds, index + 1);
                waitForInteraction(node);
                return;
            }
            if (node.canContinueExecution()) {
                runnableNodes.add(node);
            } else {
                runQueue.addLast(nodeId);
            }
        }

        if (runnableNodes.isEmpty()) {
            throw new IllegalStateException("No runnable nodes available. Check graph dependencies.");
        }

        Locale locale = LocaleContextHolder.getLocale();
        List<CompletableFuture<NodeRunResult>> futures = new ArrayList<>();
        for (BaseNode node : runnableNodes) {
            if (asyncMode) {
                futures.add(threadPool.submitNodeTask(new RunGraphNodeTask(node, locale)));
            } else {
                futures.add(CompletableFuture.completedFuture(new RunGraphNodeTask(node, locale).call()));
            }
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        for (int i = 0; i < runnableNodes.size(); i++) {
            BaseNode node = runnableNodes.get(i);
            NodeRunResult runResult = futures.get(i).join();
            if (runResult.getStatus() != com.itao.workflowEngine.graph.model.enums.NodeRunStatus.COMPLETED) {
                throw new IllegalStateException(runResult.getReason());
            }
            monitor.incrementDoCount();
            if (node.shouldPauseAfterRun()) {
                waitForInteraction(node);
                return;
            }
            routeNextNodes(node);
        }
        saveCheckpointSnapshot();
    }

    private void routeNextNodes(BaseNode node) {
        for (String targetNodeId : node.routeNode()) {
            BaseNode targetNode = getOrCreateNodeInstance(targetNodeId);
            if (targetNode != null) {
                targetNode.setRunSourceNodeId(node.getId());
            }
            if (!runQueue.contains(targetNodeId)) {
                runQueue.addLast(targetNodeId);
            }
            graphState.markNodeScheduled(targetNodeId);
        }
    }

    private BaseNode getOrCreateNodeInstance(String nodeId) {
        return nodeInstanceMap.computeIfAbsent(nodeId, key -> {
            BaseNodeData nodeData = allNodeDataMap.get(key);
            if (nodeData == null) {
                return null;
            }
            return NodeFactory.createNode(
                    nodeData,
                    workflowDTO.getId(),
                    userId,
                    graphState,
                    edgeManage.getTargetEdges(key),
                    edgeManage.getSourceEdges(key),
                    nodeMaxSteps,
                    callback
            );
        });
    }

    private void fail(String reason) {
        this.status = GraphEngineStatus.FAILED;
        this.reason = reason == null ? "Unknown workflow error" : reason;
    }

    private boolean shouldInterruptBefore(List<String> batchNodeIds) {
        Set<String> interruptBeforeNodeIds = executionOptions.getInterruptBeforeNodeIds();
        if (interruptBeforeNodeIds == null || interruptBeforeNodeIds.isEmpty()) {
            return false;
        }
        for (String nodeId : batchNodeIds) {
            if (interruptBeforeNodeIds.contains(nodeId)) {
                return true;
            }
        }
        return false;
    }

    private void requeueBatch(List<String> batchNodeIds) {
        for (int index = batchNodeIds.size() - 1; index >= 0; index--) {
            runQueue.addFirst(batchNodeIds.get(index));
        }
    }

    private void requeueBatchForInteraction(List<BaseNode> runnableNodes, List<String> batchNodeIds, int nextIndex) {
        for (BaseNode runnableNode : runnableNodes) {
            runQueue.addLast(runnableNode.getId());
        }
        for (int index = nextIndex; index < batchNodeIds.size(); index++) {
            runQueue.addLast(batchNodeIds.get(index));
        }
    }

    private void interrupt(String reason, List<String> pendingNodeIds) {
        this.status = GraphEngineStatus.INTERRUPTED;
        this.reason = reason;
        if (pendingNodeIds != null && !pendingNodeIds.isEmpty()) {
            saveCheckpointSnapshot();
        }
    }

    private void waitForInteraction(BaseNode node) {
        InteractionContext interactionContext = node.buildInteractionContext();
        if (interactionContext == null) {
            throw new IllegalStateException("Interaction node did not provide an interaction context.");
        }
        if (interactionContext.getNodeId() == null || interactionContext.getNodeId().isBlank()) {
            interactionContext.setNodeId(node.getId());
        }
        if (interactionContext.getNodeName() == null || interactionContext.getNodeName().isBlank()) {
            interactionContext.setNodeName(node.getName());
        }
        if (interactionContext.getNodeType() == null || interactionContext.getNodeType().isBlank()) {
            interactionContext.setNodeType(node.getType());
        }
        graphState.setPendingInteraction(interactionContext);
        status = GraphEngineStatus.WAITING;
        reason = interactionContext.getReason() == null ? "Waiting for interaction." : interactionContext.getReason();
        saveCheckpointSnapshot();
    }

    private void saveCheckpointSnapshot() {
        GraphCheckpointSnapshot snapshot = new GraphCheckpointSnapshot();
        snapshot.setRunId(runId);
        snapshot.setWorkflowId(workflowDTO.getId());
        snapshot.setUserId(userId);
        snapshot.setAsyncMode(asyncMode);
        snapshot.setWorkflow(workflowDTO);
        snapshot.setPendingNodeIds(new ArrayList<>(runQueue));
        snapshot.setDoCount(monitor.getDoCount());
        snapshot.setStatus(status);
        snapshot.setReason(reason);
        snapshot.setNodeVariables(graphState.snapshotVariables());
        snapshot.setChatContext(graphState.snapshotChatContext());
        snapshot.setAgentTrace(graphState.snapshotAgentTrace());
        snapshot.setToolCalls(graphState.snapshotToolCalls());
        snapshot.setPendingToolCalls(graphState.snapshotPendingToolCalls());
        snapshot.setToolResults(graphState.snapshotToolResults());
        snapshot.setNodeSignals(graphState.snapshotNodeSignals());
        snapshot.setScheduledNodeIds(graphState.snapshotScheduledNodes());
        snapshot.setInteractionContext(graphState.snapshotInteractionContext());
        lastCheckpointId = checkpointStore.save(snapshot);
    }

    private void restoreCheckpoint(GraphCheckpointSnapshot snapshot) {
        graphState.restoreSnapshot(
                snapshot.getNodeVariables(),
                snapshot.getChatContext(),
                snapshot.getAgentTrace(),
                snapshot.getToolCalls(),
                snapshot.getPendingToolCalls(),
                snapshot.getToolResults(),
                snapshot.getNodeSignals(),
                snapshot.getScheduledNodeIds(),
                snapshot.getInteractionContext()
        );
        runQueue.clear();
        runQueue.addAll(snapshot.getPendingNodeIds());
        monitor.setDoCount(snapshot.getDoCount());
        status = snapshot.getStatus() == null ? GraphEngineStatus.WAITING : snapshot.getStatus();
        reason = snapshot.getReason() == null ? "" : snapshot.getReason();
        lastCheckpointId = snapshot.getCheckpointId() == null ? "" : snapshot.getCheckpointId();
    }

    private void applyInitialInput(Map<String, Object> inputData) {
        if (inputData == null || inputData.isEmpty()) {
            return;
        }
        Object rawChatContext = inputData.get("chatContext");
        if (rawChatContext instanceof List<?> list) {
            List<Map<String, String>> contexts = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, String> context = new LinkedHashMap<>();
                    Object role = map.get("role");
                    Object message = map.get("message");
                    if (message == null) {
                        continue;
                    }
                    context.put("role", role == null ? "human" : String.valueOf(role));
                    context.put("message", String.valueOf(message));
                    contexts.add(context);
                }
            }
            graphState.appendContexts(contexts);
        }
    }

    public GraphState getGraphState() {
        return graphState;
    }

    public FlowWsDTO getWorkflowDTO() {
        return workflowDTO;
    }

    public GraphEngineMonitor getMonitor() {
        return monitor;
    }

    public GraphEngineStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Map<String, BaseNode> getNodeInstanceMap() {
        return new LinkedHashMap<>(nodeInstanceMap);
    }

    public String getRunId() {
        return runId;
    }

    public String getLastCheckpointId() {
        return lastCheckpointId;
    }

    public List<String> snapshotPendingNodeIds() {
        return new ArrayList<>(runQueue);
    }

    @Override
    public void close() {
        graphState.clear();
        nodeInstanceMap.clear();
        runQueue.clear();
    }
}
