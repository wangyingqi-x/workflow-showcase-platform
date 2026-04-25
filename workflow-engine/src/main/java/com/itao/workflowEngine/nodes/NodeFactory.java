package com.itao.workflowEngine.nodes;

import com.itao.pojo.workflow.enums.NodeTypeEnum;
import com.itao.util.MessageUtils;
import com.itao.workflowEngine.callback.BaseCallback;
import com.itao.workflowEngine.common.BaseNodeData;
import com.itao.workflowEngine.edges.EdgeBase;
import com.itao.workflowEngine.graph.GraphState;
import com.itao.workflowEngine.nodes.agent.AgentNode;
import com.itao.workflowEngine.nodes.agent.ToolNode;
import com.itao.workflowEngine.nodes.basic.condition.ConditionNode;
import com.itao.workflowEngine.nodes.basic.end.EndNode;
import com.itao.workflowEngine.nodes.basic.input.InputNode;
import com.itao.workflowEngine.nodes.basic.output.OutputNode;
import com.itao.workflowEngine.nodes.basic.polymerize.RunWaitingNode;
import com.itao.workflowEngine.nodes.basic.start.StartNode;

import java.util.List;

public final class NodeFactory {

    private NodeFactory() {
    }

    public static BaseNode createNode(BaseNodeData nodeData,
                                      String workflowId,
                                      String userId,
                                      GraphState graphState,
                                      List<EdgeBase> targetEdges,
                                      List<EdgeBase> sourceEdges,
                                      int maxSteps,
                                      BaseCallback callback) {
        NodeTypeEnum nodeType = NodeTypeEnum.fromString(nodeData.getType());
        if (nodeType == null) {
            throw new IllegalArgumentException(MessageUtils.getMsg("NodeFactory.nodeType.not.support", new Object[]{nodeData.getType()}));
        }
        return switch (nodeType) {
            case START -> new StartNode(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
            case INPUT -> new InputNode(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
            case OUTPUT -> new OutputNode(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
            case AGENT -> new AgentNode(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
            case TOOL -> new ToolNode(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
            case CONDITION -> new ConditionNode(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
            case RUNWAIT -> new RunWaitingNode(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
            case END -> new EndNode(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
            default -> throw new IllegalArgumentException(MessageUtils.getMsg("NodeFactory.Node.creator.notFound", new Object[]{nodeType}));
        };
    }
}
