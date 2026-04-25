package com.itao.workflowEngine.agent.tool;

import com.itao.workflowEngine.agent.model.ToolExecutionResult;
import com.itao.workflowEngine.graph.GraphState;

import java.util.Map;

public interface ToolExecutor {

    ToolExecutionResult execute(Map<String, Object> arguments, GraphState graphState);
}
