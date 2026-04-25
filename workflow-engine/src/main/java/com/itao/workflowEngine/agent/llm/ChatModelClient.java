package com.itao.workflowEngine.agent.llm;

import com.itao.workflowEngine.agent.model.AgentDecision;
import com.itao.workflowEngine.agent.model.AgentPromptContext;

public interface ChatModelClient {

    AgentDecision chat(AgentPromptContext context);
}
