package com.itao.demo.service;

import com.itao.demo.callback.InMemoryDemoCallback;
import com.itao.demo.dto.DemoWorkflowRequest;
import com.itao.demo.dto.DemoWorkflowResumeRequest;
import com.itao.pojo.workflow.ChatResponse;
import com.itao.pojo.workflow.ViewPort;
import com.itao.pojo.workflow.enums.NodeTypeEnum;
import com.itao.workflowEngine.agent.llm.AgentMessageStreamListener;
import com.itao.workflowEngine.agent.tool.McpDynamicRegistryService;
import com.itao.workflowEngine.agent.tool.ToolDefinition;
import com.itao.workflowEngine.agent.tool.ToolRegistry;
import com.itao.workflowEngine.callback.BaseCallback;
import com.itao.workflowEngine.checkpoint.CheckpointStore;
import com.itao.workflowEngine.checkpoint.GraphCheckpointSnapshot;
import com.itao.workflowEngine.common.BaseNodeData;
import com.itao.workflowEngine.common.NodeGroupParam;
import com.itao.workflowEngine.common.NodeParam;
import com.itao.workflowEngine.common.bo.NodeDataBO;
import com.itao.workflowEngine.common.dto.FlowWsDTO;
import com.itao.workflowEngine.common.enums.NodeGroupParamKeyEnum;
import com.itao.workflowEngine.edges.EdgeBase;
import com.itao.workflowEngine.graph.GraphEngine;
import com.itao.workflowEngine.graph.GraphEngineFactory;
import com.itao.workflowEngine.graph.GraphStateEventListener;
import com.itao.workflowEngine.graph.model.GraphRunResult;
import com.itao.workflowEngine.graph.model.InteractionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DemoWorkflowService {

    private final ToolRegistry toolRegistry;
    private final McpDynamicRegistryService mcpDynamicRegistryService;
    private final CheckpointStore checkpointStore;
    private final String llmProvider;
    private final String llmModel;
    private final boolean llmConfigured;

    public DemoWorkflowService(ToolRegistry toolRegistry,
                               McpDynamicRegistryService mcpDynamicRegistryService,
                               CheckpointStore checkpointStore,
                               @Value("${llm.provider:${openai.provider:GLM}}") String llmProvider,
                               @Value("${llm.model:${openai.model:glm-5.1}}") String llmModel,
                               @Value("${llm.api-key:${openai.api-key:}}") String llmApiKey) {
        this.toolRegistry = toolRegistry;
        this.mcpDynamicRegistryService = mcpDynamicRegistryService;
        this.checkpointStore = checkpointStore;
        this.llmProvider = blankToDefault(llmProvider, "GLM");
        this.llmModel = blankToDefault(llmModel, "glm-5.1");
        this.llmConfigured = llmApiKey != null && !llmApiKey.trim().isBlank();
    }

    public Map<String, Object> getStudioMetadata() {
        mcpDynamicRegistryService.refreshConfiguredServersIfNeeded();

        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolDefinition definition : toolRegistry.listDefinitions()) {
            tools.add(toToolMetadata(definition));
        }

        Map<String, Object> llm = new LinkedHashMap<>();
        llm.put("provider", llmProvider);
        llm.put("model", llmModel);
        llm.put("configured", llmConfigured);

        Map<String, Object> mcp = new LinkedHashMap<>();
        mcp.put("servers", mcpDynamicRegistryService.snapshotStatuses());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("project", "workflow-showcase-platform");
        response.put("studio", "agent-studio");
        response.put("llm", llm);
        response.put("mcp", mcp);
        response.put("tools", tools);
        response.put("nodeTypes", List.of(
                nodeType("start", "Start"),
                nodeType("input", "Input"),
                nodeType("condition", "Condition"),
                nodeType("agent", "Agent"),
                nodeType("tool", "Tool"),
                nodeType("output", "Output"),
                nodeType("runwait", "RunWait"),
                nodeType("end", "End")
        ));
        response.put("capabilities", List.of(
                "Dynamic MCP import and tool discovery",
                "Generic ReAct planning with real tool execution",
                "Nodes-only orchestration showcase",
                "Human-in-the-loop waiting and resume"
        ));
        return response;
    }

    public Map<String, Object> runConfiguredWorkflow(DemoWorkflowRequest request) {
        return runInternal(request, null);
    }

    public Map<String, Object> chatConfiguredWorkflow(DemoWorkflowRequest request) {
        return runInternal(request, null);
    }

    public SseEmitter streamChatConfiguredWorkflow(DemoWorkflowRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emit(emitter, "meta", Map.of(
                    "provider", llmProvider,
                    "model", llmModel
            ));
            Map<String, Object> response = runInternal(request, emitter);
            emit(emitter, "result", response);
            emit(emitter, "done", Map.of(
                    "status", response.getOrDefault("status", "COMPLETED"),
                    "assistantReply", response.getOrDefault("assistantReply", "")
            ));
            emitter.complete();
        } catch (RuntimeException ex) {
            safelyEmitError(emitter, ex);
        }
        return emitter;
    }

    public Map<String, Object> resumeConfiguredWorkflow(DemoWorkflowResumeRequest request) {
        if (request == null || request.getCheckpointId() == null || request.getCheckpointId().isBlank()) {
            throw new IllegalArgumentException("checkpointId is required.");
        }
        GraphCheckpointSnapshot snapshot = checkpointStore.load(request.getCheckpointId());
        if (snapshot == null) {
            throw new IllegalArgumentException("Checkpoint not found: " + request.getCheckpointId());
        }

        InMemoryDemoCallback callback = new InMemoryDemoCallback();
        GraphEngine engine = GraphEngineFactory.getInstance().getNewEngineInstanceByCheckpoint(snapshot, callback);
        try {
            engine.resume(request.getInteractionData());
            return buildWorkflowResponse(engine, callback);
        } finally {
            engine.close();
        }
    }

    private Map<String, Object> runInternal(DemoWorkflowRequest request, SseEmitter emitter) {
        validateRequest(request);
        FlowWsDTO workflow = toWorkflow(request);
        InMemoryDemoCallback callback = createCallback(emitter);
        GraphEngine engine = GraphEngineFactory.getInstance()
                .getNewEngineInstanceByData("demo-user", workflow, !Boolean.FALSE.equals(request.getAsyncMode()), callback);

        if (emitter != null) {
            engine.getGraphState().setAgentMessageStreamListener(new DemoAgentMessageStreamListener(emitter));
            engine.getGraphState().setGraphStateEventListener(new DemoGraphStateEventListener(emitter));
        }

        try {
            Map<String, Object> inputData = new LinkedHashMap<>();
            inputData.put("chatContext", toChatContext(request.getConversation()));
            GraphRunResult ignored = engine.run(inputData);
            return buildWorkflowResponse(engine, callback);
        } finally {
            engine.close();
        }
    }

    private Map<String, Object> buildWorkflowResponse(GraphEngine engine, BaseCallback callback) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("project", "workflow-showcase-platform");
        response.put("workflowId", engine.getWorkflowDTO().getId());
        response.put("workflowName", engine.getWorkflowDTO().getName());
        response.put("status", engine.getStatus().name());
        response.put("reason", engine.getReason());
        response.put("runId", engine.getRunId());
        response.put("checkpointId", engine.getLastCheckpointId());
        response.put("pendingNodeIds", engine.snapshotPendingNodeIds());
        response.put("stepsExecuted", engine.getMonitor().getDoCount());
        response.put("variables", engine.getGraphState().snapshotVariables());
        response.put("chatContext", engine.getGraphState().snapshotChatContext());
        response.put("assistantReply", readAssistantReply(
                engine.getStatus().name(),
                engine.getGraphState().snapshotChatContext(),
                engine.getGraphState().snapshotVariables()
        ));
        response.put("logs", callback.getNodeLogs());
        response.put("agentTrace", engine.getGraphState().snapshotAgentTrace());
        response.put("toolCalls", engine.getGraphState().snapshotToolCalls());
        response.put("toolResults", engine.getGraphState().snapshotToolResults());
        response.put("interactionContext", toInteractionMetadata(engine.getGraphState().snapshotInteractionContext()));
        response.put("notes", List.of(
                "Responses come from the real workflow-engine runtime, not frontend mock data.",
                "Agent nodes plan and synthesize, while tool nodes execute real MCP tools through the registry."
        ));
        return response;
    }

    private Map<String, Object> toInteractionMetadata(InteractionContext interactionContext) {
        if (interactionContext == null) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("nodeId", interactionContext.getNodeId());
        item.put("nodeName", interactionContext.getNodeName());
        item.put("nodeType", interactionContext.getNodeType());
        item.put("interactionType", interactionContext.getInteractionType() == null ? null : interactionContext.getInteractionType().name());
        item.put("reason", interactionContext.getReason());
        item.put("payload", interactionContext.getPayload() == null ? Map.of() : new LinkedHashMap<>(interactionContext.getPayload()));
        return item;
    }

    private FlowWsDTO toWorkflow(DemoWorkflowRequest request) {
        FlowWsDTO workflow = new FlowWsDTO();
        workflow.setId(blankToDefault(request.getWorkflowId(), "studio-workflow-" + Instant.now().toEpochMilli()));
        workflow.setName(blankToDefault(request.getWorkflowName(), "Demo Workflow"));
        workflow.setDescription(blankToDefault(request.getDescription(), "Workflow assembled from the Studio request."));
        workflow.setViewport(defaultViewport());
        workflow.setNodes(toNodes(request.getNodes()));
        workflow.setEdges(toEdges(request.getEdges()));
        return workflow;
    }

    private List<NodeDataBO> toNodes(List<DemoWorkflowRequest.DemoNodeConfig> nodeConfigs) {
        List<NodeDataBO> nodes = new ArrayList<>();
        if (nodeConfigs == null) {
            return nodes;
        }
        for (DemoWorkflowRequest.DemoNodeConfig config : nodeConfigs) {
            if (config == null) {
                continue;
            }
            BaseNodeData data = new BaseNodeData();
            data.setId(config.getId());
            data.setType(config.getType());
            data.setName(blankToDefault(config.getName(), defaultNodeName(config.getType())));
            data.setDescription(blankToDefault(config.getDescription(), data.getName()));

            Map<String, Object> flattenedParams = new LinkedHashMap<>();
            if (config.getParams() != null) {
                flattenedParams.putAll(config.getParams());
            }
            if (config.getGuideWord() != null && !config.getGuideWord().isBlank()) {
                flattenedParams.put(NodeGroupParamKeyEnum.GUIDE_WORD.getKey(), config.getGuideWord());
            }
            if (config.getGuideQuestions() != null && !config.getGuideQuestions().isEmpty()) {
                flattenedParams.put(NodeGroupParamKeyEnum.GUIDE_QUESTION.getKey(), new ArrayList<>(config.getGuideQuestions()));
            }
            if (config.getMessage() != null && !config.getMessage().isBlank()) {
                flattenedParams.put(NodeGroupParamKeyEnum.MESSAGE.getKey(), config.getMessage());
            }
            if (config.getDelayMs() != null) {
                flattenedParams.put("delay_ms", config.getDelayMs());
            }

            NodeGroupParam groupParam = new NodeGroupParam();
            groupParam.setName("default");
            List<NodeParam> params = new ArrayList<>();
            for (Map.Entry<String, Object> entry : flattenedParams.entrySet()) {
                NodeParam param = new NodeParam();
                param.setKey(entry.getKey());
                param.setValue(entry.getValue());
                params.add(param);
            }
            groupParam.setParams(params);
            data.setGroupParams(List.of(groupParam));

            NodeDataBO node = new NodeDataBO();
            node.setId(config.getId());
            node.setType(config.getType());
            node.setData(data);
            nodes.add(node);
        }
        return nodes;
    }

    private List<EdgeBase> toEdges(List<DemoWorkflowRequest.DemoEdgeConfig> edgeConfigs) {
        List<EdgeBase> edges = new ArrayList<>();
        if (edgeConfigs == null) {
            return edges;
        }
        int index = 1;
        for (DemoWorkflowRequest.DemoEdgeConfig config : edgeConfigs) {
            if (config == null) {
                continue;
            }
            EdgeBase edge = new EdgeBase();
            edge.setId("edge-" + index++);
            edge.setSource(config.getSource());
            edge.setTarget(config.getTarget());
            edge.setSourceHandle(config.getSourceHandle());
            edges.add(edge);
        }
        return edges;
    }

    private List<Map<String, String>> toChatContext(List<DemoWorkflowRequest.ConversationMessage> messages) {
        List<Map<String, String>> chatContext = new ArrayList<>();
        if (messages == null) {
            return chatContext;
        }
        for (DemoWorkflowRequest.ConversationMessage message : messages) {
            if (message == null || message.getMessage() == null || message.getMessage().isBlank()) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("role", blankToDefault(message.getRole(), "human"));
            item.put("message", message.getMessage());
            chatContext.add(item);
        }
        return chatContext;
    }

    private InMemoryDemoCallback createCallback(SseEmitter emitter) {
        if (emitter == null) {
            return new InMemoryDemoCallback();
        }
        return new InMemoryDemoCallback(event -> emit(emitter, "log", toLogPayload(event)));
    }

    private Map<String, Object> toLogPayload(ChatResponse event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", event.getEventType());
        payload.put("nodeId", event.getNodeId());
        payload.put("nodeName", event.getNodeName());
        payload.put("message", event.getMessage());
        payload.put("payload", event.getPayload());
        payload.put("timestamp", event.getTimestamp() == null ? null : event.getTimestamp().toString());
        return payload;
    }

    private Map<String, Object> toToolMetadata(ToolDefinition definition) {
        String toolName = definition.getName();
        String serverName = "";
        String shortName = toolName;
        String type = "local";
        int separatorIndex = toolName.indexOf("__");
        if (separatorIndex > 0) {
            serverName = toolName.substring(0, separatorIndex);
            shortName = toolName.substring(separatorIndex + 2);
            type = "mcp";
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", toolName);
        item.put("shortName", shortName);
        item.put("serverName", serverName);
        item.put("description", definition.getDescription());
        item.put("inputSchema", definition.getInputSchema());
        item.put("type", type);
        return item;
    }

    private Map<String, Object> nodeType(String type, String label) {
        return Map.of(
                "type", type,
                "label", label
        );
    }

    private ViewPort defaultViewport() {
        ViewPort viewPort = new ViewPort();
        viewPort.setX(0D);
        viewPort.setY(0D);
        viewPort.setZoom(0.9D);
        return viewPort;
    }

    private void validateRequest(DemoWorkflowRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (request.getNodes() == null || request.getNodes().isEmpty()) {
            throw new IllegalArgumentException("Workflow nodes are required.");
        }
        if (request.getEdges() == null || request.getEdges().isEmpty()) {
            throw new IllegalArgumentException("Workflow edges are required.");
        }
    }

    private String readAssistantReply(String status,
                                      List<Map<String, String>> chatContext,
                                      Map<String, Map<String, Object>> variables) {
        if ("WAITING".equalsIgnoreCase(status)) {
            return "";
        }
        if (chatContext != null) {
            for (int index = chatContext.size() - 1; index >= 0; index--) {
                Map<String, String> item = chatContext.get(index);
                String role = item.get("role");
                String message = item.get("message");
                if (message == null || message.isBlank()) {
                    continue;
                }
                if (Objects.equals("assistant", normalizeRole(role)) || Objects.equals("ai", normalizeRole(role))) {
                    return message;
                }
            }
        }
        if (variables != null) {
            for (Map<String, Object> nodeVariables : variables.values()) {
                Object finalAnswer = nodeVariables.get("final_answer");
                if (finalAnswer instanceof String text && !text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private String normalizeRole(String role) {
        return role == null ? "human" : role.trim().toLowerCase();
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.trim().isBlank() ? defaultValue : value.trim();
    }

    private String defaultNodeName(String type) {
        NodeTypeEnum nodeType = NodeTypeEnum.fromString(type);
        if (nodeType == null) {
            return "Node";
        }
        return switch (nodeType) {
            case START -> "Start";
            case INPUT -> "Input";
            case CONDITION -> "Condition";
            case AGENT -> "Agent";
            case TOOL -> "Tool";
            case OUTPUT -> "Output";
            case RUNWAIT -> "RunWait";
            case END -> "End";
            case WORKFLOW -> "Workflow";
        };
    }

    private void emit(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to stream " + eventName + " event.", ex);
        }
    }

    private void safelyEmitError(SseEmitter emitter, RuntimeException ex) {
        try {
            emit(emitter, "error", Map.of(
                    "message", ex.getMessage() == null ? "Streaming workflow failed." : ex.getMessage()
            ));
        } catch (RuntimeException ignored) {
        }
        emitter.completeWithError(ex);
    }

    private final class DemoGraphStateEventListener implements GraphStateEventListener {

        private final SseEmitter emitter;

        private DemoGraphStateEventListener(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onAgentTrace(Map<String, Object> traceItem) {
            emit(emitter, "trace", traceItem);
        }

        @Override
        public void onToolCall(Map<String, Object> toolCall) {
            emit(emitter, "tool_call", toolCall);
        }

        @Override
        public void onToolResult(String toolName, Map<String, Object> result) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("toolName", toolName);
            payload.put("result", result);
            emit(emitter, "tool_result", payload);
        }

        @Override
        public void onInteractionState(InteractionContext interactionContext) {
            emit(emitter, "interaction", toInteractionMetadata(interactionContext));
        }
    }

    private final class DemoAgentMessageStreamListener implements AgentMessageStreamListener {

        private final SseEmitter emitter;
        private final Map<String, String> stages = new LinkedHashMap<>();

        private DemoAgentMessageStreamListener(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onStart(String nodeId, String stage) {
            stages.put(nodeId, stage == null ? "" : stage);
            emit(emitter, "agent_start", Map.of(
                    "nodeId", nodeId,
                    "stage", stage == null ? "" : stage
            ));
        }

        @Override
        public void onDelta(String nodeId, String delta) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("nodeId", nodeId);
            payload.put("stage", stages.getOrDefault(nodeId, ""));
            payload.put("content", delta == null ? "" : delta);
            emit(emitter, "delta", payload);
        }

        @Override
        public void onComplete(String nodeId, String fullMessage) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("nodeId", nodeId);
            payload.put("stage", stages.getOrDefault(nodeId, ""));
            payload.put("content", fullMessage == null ? "" : fullMessage);
            emit(emitter, "agent_complete", payload);
        }
    }
}
