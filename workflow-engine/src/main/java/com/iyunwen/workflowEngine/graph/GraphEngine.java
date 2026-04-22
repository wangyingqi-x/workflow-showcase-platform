package com.iyunwen.workflowEngine.graph;

import com.iyunwen.pojo.workflow.enums.NodeTypeEnum;
import com.iyunwen.util.YWMessageUtils;
import com.iyunwen.util.spring.YWSpringBeanUtil;
import com.iyunwen.workflowEngine.callback.BaseCallback;
import com.iyunwen.workflowEngine.common.BaseNodeData;
import com.iyunwen.workflowEngine.common.dto.FlowWsDTO;
import com.iyunwen.workflowEngine.config.WorkflowConfig;
import com.iyunwen.workflowEngine.edges.EdgeManage;
import com.iyunwen.workflowEngine.graph.model.GraphEngineMonitor;
import com.iyunwen.workflowEngine.graph.model.GraphRunResult;
import com.iyunwen.workflowEngine.graph.model.NodeRunResult;
import com.iyunwen.workflowEngine.graph.model.enums.GraphEngineStatus;
import com.iyunwen.workflowEngine.graph.thread.RunGraphNodeTask;
import com.iyunwen.workflowEngine.graph.thread.RunGraphNodeThreadPool;
import com.iyunwen.workflowEngine.nodes.BaseNode;
import com.iyunwen.workflowEngine.nodes.NodeFactory;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final int graphMaxSteps;
    private final int nodeMaxSteps;
    private final RunGraphNodeThreadPool threadPool;

    private GraphEngineStatus status = GraphEngineStatus.WAITING;
    private String reason = "";

    GraphEngine(String userId,
                boolean asyncMode,
                FlowWsDTO workflowDTO,
                EdgeManage edgeManage,
                Map<String, BaseNodeData> allNodeDataMap,
                BaseCallback callback) {
        this.userId = userId;
        this.asyncMode = asyncMode;
        this.workflowDTO = workflowDTO;
        this.edgeManage = edgeManage;
        this.allNodeDataMap = allNodeDataMap;
        this.callback = callback;

        WorkflowConfig config;
        try {
            config = YWSpringBeanUtil.getBean(WorkflowConfig.class);
        } catch (Exception ex) {
            config = new WorkflowConfig();
        }
        this.graphMaxSteps = config.getMaxSteps();
        this.nodeMaxSteps = config.getGraphEngine().getNodeMaxSteps();
        this.threadPool = YWSpringBeanUtil.getBean(RunGraphNodeThreadPool.class);
    }

    public GraphRunResult run(Map<String, Object> inputData) {
        callback.onWorkflowStart(workflowDTO.getId());
        monitor.setStartRunTime(System.currentTimeMillis());
        status = GraphEngineStatus.RUNNING;
        reason = "";
        enqueueStartNode();

        try {
            while (!runQueue.isEmpty() && status == GraphEngineStatus.RUNNING) {
                if (monitor.getDoCount() >= graphMaxSteps) {
                    fail(YWMessageUtils.getMsg("GraphEngine.max.steps.reached"));
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

    private void enqueueStartNode() {
        for (Map.Entry<String, BaseNodeData> entry : allNodeDataMap.entrySet()) {
            if (NodeTypeEnum.START.getType().equalsIgnoreCase(entry.getValue().getType())) {
                runQueue.addLast(entry.getKey());
                return;
            }
        }
        throw new IllegalStateException(YWMessageUtils.getMsg("Graph.engine.noStartNode"));
    }

    private void executeCurrentBatch() {
        List<String> batchNodeIds = new ArrayList<>();
        while (!runQueue.isEmpty()) {
            batchNodeIds.add(runQueue.removeFirst());
        }

        List<BaseNode> runnableNodes = new ArrayList<>();
        for (String nodeId : batchNodeIds) {
            BaseNode node = getOrCreateNodeInstance(nodeId);
            if (node == null) {
                continue;
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
            if (runResult.getStatus() != com.iyunwen.workflowEngine.graph.model.enums.NodeRunStatus.COMPLETED) {
                throw new IllegalStateException(runResult.getReason());
            }
            monitor.incrementDoCount();
            routeNextNodes(node);
        }
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

    @Override
    public void close() {
        graphState.clear();
        nodeInstanceMap.clear();
        runQueue.clear();
    }
}
