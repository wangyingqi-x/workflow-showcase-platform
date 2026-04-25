package com.itao.workflowEngine.agent.model;

import java.util.ArrayList;
import java.util.List;

public class AgentDecision {

    private AgentDecisionType decisionType = AgentDecisionType.FINAL_ANSWER;
    private String decisionSource;
    private String assistantMessage;
    private String finalAnswer;
    private List<AgentToolCall> toolCalls = new ArrayList<>();

    public AgentDecisionType getDecisionType() {
        return decisionType;
    }

    public void setDecisionType(AgentDecisionType decisionType) {
        this.decisionType = decisionType;
    }

    public String getDecisionSource() {
        return decisionSource;
    }

    public void setDecisionSource(String decisionSource) {
        this.decisionSource = decisionSource;
    }

    public String getAssistantMessage() {
        return assistantMessage;
    }

    public void setAssistantMessage(String assistantMessage) {
        this.assistantMessage = assistantMessage;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public List<AgentToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<AgentToolCall> toolCalls) {
        this.toolCalls = toolCalls == null ? new ArrayList<>() : new ArrayList<>(toolCalls);
    }
}
