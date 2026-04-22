package com.iyunwen.workflowEngine.nodes;

import com.iyunwen.pojo.workflow.enums.NodeTypeEnum;
import com.iyunwen.util.YWMessageUtils;
import com.iyunwen.workflowEngine.callback.BaseCallback;
import com.iyunwen.workflowEngine.common.BaseNodeData;
import com.iyunwen.workflowEngine.edges.EdgeBase;
import com.iyunwen.workflowEngine.graph.GraphState;
import com.iyunwen.workflowEngine.nodes.basic.end.EndNode;
import com.iyunwen.workflowEngine.nodes.basic.output.OutputNode;
import com.iyunwen.workflowEngine.nodes.basic.polymerize.RunWaitingNode;
import com.iyunwen.workflowEngine.nodes.basic.start.StartNode;

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
            throw new IllegalArgumentException(YWMessageUtils.getMsg("NodeFactory.nodeType.not.support", new Object[]{nodeData.getType()}));
        }
        return switch (nodeType) {
            case START -> new StartNode(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
            case OUTPUT -> new OutputNode(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
            case RUNWAIT -> new RunWaitingNode(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
            case END -> new EndNode(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
            default -> throw new IllegalArgumentException(YWMessageUtils.getMsg("NodeFactory.Node.creator.notFound", new Object[]{nodeType}));
        };
    }
}
