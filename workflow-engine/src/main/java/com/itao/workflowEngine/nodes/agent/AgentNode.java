package com.itao.workflowEngine.nodes.agent;

import com.itao.workflowEngine.agent.llm.ChatModelClient;
import com.itao.workflowEngine.agent.model.AgentDecision;
import com.itao.workflowEngine.agent.model.AgentDecisionType;
import com.itao.workflowEngine.agent.model.AgentPromptContext;
import com.itao.workflowEngine.agent.model.AgentToolCall;
import com.itao.workflowEngine.callback.BaseCallback;
import com.itao.workflowEngine.common.BaseNodeData;
import com.itao.workflowEngine.common.enums.RoleType;
import com.itao.workflowEngine.edges.EdgeBase;
import com.itao.workflowEngine.graph.GraphState;
import com.itao.workflowEngine.graph.model.InteractionContext;
import com.itao.workflowEngine.graph.model.InteractionType;
import com.itao.workflowEngine.nodes.BaseNode;
import com.itao.util.spring.SpringBeanProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentNode extends BaseNode {

    private static final String CONFIRMATION_REQUIRED_KEY = "require_human_confirmation";
    private static final String AWAITING_CONFIRMATION_KEY = "awaiting_confirmation";
    private static final String CONFIRMATION_ACTION_KEY = "confirmation_action";
    private static final String CONFIRMATION_FEEDBACK_KEY = "confirmation_feedback";
    private static final String PROPOSED_DECISION_TYPE_KEY = "proposed_decision_type";
    private static final String PROPOSED_TOOL_CALLS_KEY = "proposed_tool_calls";
    private static final String PROPOSED_SELECTED_TARGETS_KEY = "proposed_selected_targets";
    private static final String PROPOSED_ASSISTANT_MESSAGE_KEY = "proposed_assistant_message";
    private static final String PROPOSED_DECISION_SOURCE_KEY = "proposed_decision_source";
    private static final String PROPOSED_FINAL_ANSWER_KEY = "proposed_final_answer";

    private final ChatModelClient chatModelClient;

    public AgentNode(BaseNodeData nodeData, String workflowId, String userId, GraphState graphState,
                     List<EdgeBase> targetEdges, List<EdgeBase> sourceEdges, int maxSteps, BaseCallback callback) {
        super(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
        this.chatModelClient = SpringBeanProvider.getBean(ChatModelClient.class);
    }

    @Override
    public Map<String, Object> _run(String uniqueId) {
        if (hasPendingConfirmationResult()) {
            return handleConfirmedDecision();
        }

        int reactIteration = resolveCurrentIteration();
        int reactMaxIterations = resolvePositiveInt(nodeParams.get("react_max_iterations"), 3);
        boolean reactLoopEnabled = booleanValue(nodeParams.get("react_loop_enabled"));

        Map<String, Object> runtimeParams = new LinkedHashMap<>(nodeParams);
        runtimeParams.put("react_iteration", reactIteration);
        runtimeParams.put("react_max_iterations", reactMaxIterations);
        runtimeParams.put("react_loop_enabled", reactLoopEnabled);

        AgentPromptContext context = new AgentPromptContext();
        context.setWorkflowId(workflowId);
        context.setNodeId(id);
        context.setUserId(userId);
        context.setNodeParams(runtimeParams);
        context.setVariables(graphState.snapshotVariables());
        context.setToolResults(graphState.snapshotToolResults());
        context.setChatContext(graphState.snapshotChatContext());
        context.setStreamListener(graphState.getAgentMessageStreamListener());

        AgentDecision decision = chatModelClient.chat(context);
        Map<String, Object> tracePayload = new LinkedHashMap<>();
        tracePayload.put("decisionType", decision.getDecisionType().name());
        tracePayload.put("toolCallCount", decision.getToolCalls().size());
        tracePayload.put("reactIteration", reactIteration);
        tracePayload.put("reactMaxIterations", reactMaxIterations);
        tracePayload.put("reactLoopEnabled", reactLoopEnabled);
        tracePayload.put("decisionSource", stringValue(decision.getDecisionSource()));
        graphState.addAgentTrace(id, "decision", decision.getAssistantMessage(), tracePayload);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("decision_type", decision.getDecisionType().name());
        result.put("react_iteration", reactIteration);
        result.put("react_max_iterations", reactMaxIterations);
        result.put("react_loop_enabled", reactLoopEnabled);
        if (!stringValue(decision.getDecisionSource()).isBlank()) {
            result.put("decision_source", decision.getDecisionSource());
        }
        if (decision.getAssistantMessage() != null) {
            result.put("assistant_message", decision.getAssistantMessage());
        }

        if (decision.getDecisionType() == AgentDecisionType.TOOL_CALLS) {
            if (requiresHumanConfirmation()) {
                storeProposedToolDecision(decision);
                result.put("awaiting_confirmation", true);
                result.put("tool_calls", decision.getToolCalls().size());
                result.put("selected_targets", proposedSelectedTargets());
                return result;
            }
            if (decision.getAssistantMessage() != null && !decision.getAssistantMessage().isBlank()) {
                graphState.saveContext(decision.getAssistantMessage(), RoleType.AI);
            }
            List<String> selectedTargets = new ArrayList<>();
            for (AgentToolCall toolCall : decision.getToolCalls()) {
                graphState.addToolCall(id, toolCall.getToolName(), toolCall.getId(), toolCall.getTargetNodeId(), toolCall.getArguments());
                if (toolCall.getTargetNodeId() != null && !toolCall.getTargetNodeId().isBlank()) {
                    selectedTargets.add(toolCall.getTargetNodeId());
                }
            }
            result.put("selected_targets", selectedTargets);
            result.put("tool_calls", decision.getToolCalls().size());
            return result;
        }

        if (decision.getDecisionType() == AgentDecisionType.INTERRUPT) {
            return result;
        }

        if (requiresHumanConfirmation()) {
            storeProposedFinalAnswerDecision(decision);
            result.put("awaiting_confirmation", true);
            result.put("final_answer", decision.getFinalAnswer());
            return result;
        }

        if (decision.getFinalAnswer() != null) {
            result.put("final_answer", decision.getFinalAnswer());
        }
        if (decision.getFinalAnswer() != null && !decision.getFinalAnswer().isBlank()) {
            graphState.saveContext(decision.getFinalAnswer(), RoleType.AI);
        } else if (decision.getAssistantMessage() != null && !decision.getAssistantMessage().isBlank()) {
            graphState.saveContext(decision.getAssistantMessage(), RoleType.AI);
        }
        return result;
    }

    @Override
    public boolean shouldPauseAfterRun() {
        return booleanValue(graphState.getVariable(id, AWAITING_CONFIRMATION_KEY));
    }

    @Override
    public InteractionContext buildInteractionContext() {
        if (!shouldPauseAfterRun()) {
            return null;
        }
        InteractionContext interactionContext = new InteractionContext();
        interactionContext.setNodeId(id);
        interactionContext.setNodeName(name);
        interactionContext.setNodeType(type);
        interactionContext.setInteractionType(InteractionType.HUMAN_CONFIRM);
        String proposedDecisionType = stringValue(graphState.getVariable(id, PROPOSED_DECISION_TYPE_KEY));
        if (AgentDecisionType.FINAL_ANSWER.name().equalsIgnoreCase(proposedDecisionType)) {
            interactionContext.setReason("Review the final plan before the workflow confirms it.");
        } else {
            interactionContext.setReason("Review the proposed tool plan before the workflow continues.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reviewType", AgentDecisionType.FINAL_ANSWER.name().equalsIgnoreCase(proposedDecisionType) ? "FINAL_PLAN" : "TOOL_PLAN");
        payload.put("assistantMessage", stringValue(graphState.getVariable(id, PROPOSED_ASSISTANT_MESSAGE_KEY)));
        payload.put("decisionSource", stringValue(graphState.getVariable(id, PROPOSED_DECISION_SOURCE_KEY)));
        payload.put("selectedTargets", proposedSelectedTargets());
        payload.put("toolCalls", proposedToolCalls());
        payload.put("finalAnswer", stringValue(graphState.getVariable(id, PROPOSED_FINAL_ANSWER_KEY)));
        payload.put("actions", List.of("approve", "reject"));
        interactionContext.setPayload(payload);
        return interactionContext;
    }

    @Override
    public void handleInteractionResult(Map<String, Object> interactionData) {
        String action = stringValue(interactionData == null ? null : interactionData.get("action")).toLowerCase();
        if (!"approve".equals(action) && !"reject".equals(action)) {
            throw new IllegalArgumentException("Human confirmation requires action=approve or action=reject.");
        }
        String feedback = stringValue(interactionData == null ? null : interactionData.get("feedback"));
        graphState.setVariable(id, CONFIRMATION_ACTION_KEY, action);
        graphState.setVariable(id, CONFIRMATION_FEEDBACK_KEY, feedback);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        if (!feedback.isBlank()) {
            payload.put("feedback", feedback);
            graphState.saveContext(feedback, RoleType.HUMAN);
        }
        String proposedDecisionType = stringValue(graphState.getVariable(id, PROPOSED_DECISION_TYPE_KEY));
        String traceMessage = AgentDecisionType.FINAL_ANSWER.name().equalsIgnoreCase(proposedDecisionType)
                ? "Human review completed for the final plan."
                : "Human review completed for the proposed tool plan.";
        graphState.addAgentTrace(id, "human_confirm", traceMessage, payload);
        graphState.setVariable(id, AWAITING_CONFIRMATION_KEY, false);
    }

    @Override
    public List<String> routeNode() {
        Object decisionType = graphState.getVariable(id, "decision_type");
        if (decisionType != null && AgentDecisionType.TOOL_CALLS.name().equalsIgnoreCase(String.valueOf(decisionType))) {
            Object selectedTargets = graphState.getVariable(id, "selected_targets");
            if (selectedTargets instanceof List<?> list && !list.isEmpty()) {
                List<String> targets = new ArrayList<>();
                for (Object item : list) {
                    targets.add(String.valueOf(item));
                }
                return targets;
            }
        }
        if (decisionType != null && AgentDecisionType.INTERRUPT.name().equalsIgnoreCase(String.valueOf(decisionType))) {
            return List.of();
        }
        if (decisionType != null && AgentDecisionType.FINAL_ANSWER.name().equalsIgnoreCase(String.valueOf(decisionType))) {
            Object decisionSource = graphState.getVariable(id, "decision_source");
            if ("HUMAN_CONFIRM_REJECTED".equalsIgnoreCase(String.valueOf(decisionSource))) {
                return List.of();
            }
            String finalTarget = stringValue(nodeParams.get("final_target"));
            if (!finalTarget.isBlank()) {
                return List.of(finalTarget);
            }
        }
        return super.routeNode();
    }

    private int resolveCurrentIteration() {
        return resolvePositiveInt(graphState.getVariable(id, "react_iteration"), 0) + 1;
    }

    private boolean requiresHumanConfirmation() {
        return booleanValue(nodeParams.get(CONFIRMATION_REQUIRED_KEY));
    }

    private boolean hasPendingConfirmationResult() {
        return !stringValue(graphState.getVariable(id, CONFIRMATION_ACTION_KEY)).isBlank();
    }

    private void storeProposedToolDecision(AgentDecision decision) {
        graphState.setVariable(id, PROPOSED_DECISION_TYPE_KEY, AgentDecisionType.TOOL_CALLS.name());
        List<Map<String, Object>> storedToolCalls = new ArrayList<>();
        List<String> selectedTargets = new ArrayList<>();
        for (AgentToolCall toolCall : decision.getToolCalls()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("toolName", toolCall.getToolName());
            item.put("callId", toolCall.getId());
            item.put("targetNodeId", toolCall.getTargetNodeId());
            item.put("arguments", toolCall.getArguments() == null ? Map.of() : new LinkedHashMap<>(toolCall.getArguments()));
            storedToolCalls.add(item);
            if (toolCall.getTargetNodeId() != null && !toolCall.getTargetNodeId().isBlank()) {
                selectedTargets.add(toolCall.getTargetNodeId());
            }
        }
        graphState.setVariable(id, PROPOSED_TOOL_CALLS_KEY, storedToolCalls);
        graphState.setVariable(id, PROPOSED_SELECTED_TARGETS_KEY, selectedTargets);
        graphState.setVariable(id, PROPOSED_ASSISTANT_MESSAGE_KEY, decision.getAssistantMessage());
        graphState.setVariable(id, PROPOSED_DECISION_SOURCE_KEY, decision.getDecisionSource());
        graphState.setVariable(id, AWAITING_CONFIRMATION_KEY, true);
        graphState.removeVariable(id, CONFIRMATION_ACTION_KEY);
        graphState.removeVariable(id, CONFIRMATION_FEEDBACK_KEY);
    }

    private void storeProposedFinalAnswerDecision(AgentDecision decision) {
        graphState.setVariable(id, PROPOSED_DECISION_TYPE_KEY, AgentDecisionType.FINAL_ANSWER.name());
        graphState.removeVariable(id, PROPOSED_TOOL_CALLS_KEY);
        graphState.removeVariable(id, PROPOSED_SELECTED_TARGETS_KEY);
        graphState.setVariable(id, PROPOSED_ASSISTANT_MESSAGE_KEY, decision.getAssistantMessage());
        graphState.setVariable(id, PROPOSED_DECISION_SOURCE_KEY, decision.getDecisionSource());
        graphState.setVariable(id, PROPOSED_FINAL_ANSWER_KEY, decision.getFinalAnswer());
        graphState.setVariable(id, AWAITING_CONFIRMATION_KEY, true);
        graphState.removeVariable(id, CONFIRMATION_ACTION_KEY);
        graphState.removeVariable(id, CONFIRMATION_FEEDBACK_KEY);
    }

    private Map<String, Object> handleConfirmedDecision() {
        String action = stringValue(graphState.getVariable(id, CONFIRMATION_ACTION_KEY)).toLowerCase();
        String feedback = stringValue(graphState.getVariable(id, CONFIRMATION_FEEDBACK_KEY));
        String proposedDecisionType = stringValue(graphState.getVariable(id, PROPOSED_DECISION_TYPE_KEY));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("react_iteration", graphState.getVariable(id, "react_iteration"));
        result.put("react_max_iterations", graphState.getVariable(id, "react_max_iterations"));
        result.put("react_loop_enabled", graphState.getVariable(id, "react_loop_enabled"));

        if (AgentDecisionType.FINAL_ANSWER.name().equalsIgnoreCase(proposedDecisionType)) {
            if ("approve".equals(action)) {
                String finalAnswer = stringValue(graphState.getVariable(id, PROPOSED_FINAL_ANSWER_KEY));
                result.put("decision_type", AgentDecisionType.FINAL_ANSWER.name());
                result.put("decision_source", "HUMAN_CONFIRM_APPROVED");
                result.put("assistant_message", "Human approved the final plan.");
                result.put("final_answer", finalAnswer);
                if (!finalAnswer.isBlank()) {
                    graphState.saveContext(finalAnswer, RoleType.AI);
                }
                clearConfirmationState();
                return result;
            }

            result.put("decision_type", AgentDecisionType.FINAL_ANSWER.name());
            result.put("decision_source", "HUMAN_CONFIRM_REJECTED");
            String finalAnswer = feedback.isBlank()
                    ? "The final plan was declined by the reviewer."
                    : "The final plan was declined by the reviewer. Feedback: " + feedback;
            result.put("final_answer", finalAnswer);
            graphState.saveContext(finalAnswer, RoleType.AI);
            clearConfirmationState();
            return result;
        }

        if ("approve".equals(action)) {
            List<String> selectedTargets = proposedSelectedTargets();
            for (Map<String, Object> toolCall : proposedToolCalls()) {
                graphState.addToolCall(
                        id,
                        stringValue(toolCall.get("toolName")),
                        stringValue(toolCall.get("callId")),
                        stringValue(toolCall.get("targetNodeId")),
                        mapValue(toolCall.get("arguments"))
                );
            }
            result.put("decision_type", AgentDecisionType.TOOL_CALLS.name());
            result.put("decision_source", "HUMAN_CONFIRM_APPROVED");
            result.put("assistant_message", "Human approved the proposed tool plan.");
            result.put("selected_targets", selectedTargets);
            result.put("tool_calls", proposedToolCalls().size());
            clearConfirmationState();
            return result;
        }

        result.put("decision_type", AgentDecisionType.FINAL_ANSWER.name());
        result.put("decision_source", "HUMAN_CONFIRM_REJECTED");
        String finalAnswer = feedback.isBlank()
                ? "The proposed tool execution was declined by the reviewer."
                : "The proposed tool execution was declined by the reviewer. Feedback: " + feedback;
        result.put("final_answer", finalAnswer);
        graphState.saveContext(finalAnswer, RoleType.AI);
        clearConfirmationState();
        return result;
    }

    private void clearConfirmationState() {
        graphState.setVariable(id, AWAITING_CONFIRMATION_KEY, false);
        graphState.removeVariable(id, CONFIRMATION_ACTION_KEY);
        graphState.removeVariable(id, CONFIRMATION_FEEDBACK_KEY);
        graphState.removeVariable(id, PROPOSED_TOOL_CALLS_KEY);
        graphState.removeVariable(id, PROPOSED_SELECTED_TARGETS_KEY);
        graphState.removeVariable(id, PROPOSED_ASSISTANT_MESSAGE_KEY);
        graphState.removeVariable(id, PROPOSED_DECISION_SOURCE_KEY);
        graphState.removeVariable(id, PROPOSED_DECISION_TYPE_KEY);
        graphState.removeVariable(id, PROPOSED_FINAL_ANSWER_KEY);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> proposedToolCalls() {
        Object value = graphState.getVariable(id, PROPOSED_TOOL_CALLS_KEY);
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    Map<String, Object> toolCall = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : raw.entrySet()) {
                        toolCall.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    result.add(toolCall);
                }
            }
            return result;
        }
        return List.of();
    }

    private List<String> proposedSelectedTargets() {
        Object value = graphState.getVariable(id, PROPOSED_SELECTED_TARGETS_KEY);
        if (value instanceof List<?> list) {
            List<String> targets = new ArrayList<>();
            for (Object item : list) {
                targets.add(String.valueOf(item));
            }
            return targets;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return Map.of();
    }

    private int resolvePositiveInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value != null) {
            try {
                return Math.max(0, Integer.parseInt(String.valueOf(value).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
