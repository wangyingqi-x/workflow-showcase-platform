package com.iyunwen.demo.service;

import com.iyunwen.demo.callback.InMemoryDemoCallback;
import com.iyunwen.pojo.workflow.ViewPort;
import com.iyunwen.pojo.workflow.enums.NodeTypeEnum;
import com.iyunwen.workflowEngine.common.BaseNodeData;
import com.iyunwen.workflowEngine.common.NodeGroupParam;
import com.iyunwen.workflowEngine.common.NodeParam;
import com.iyunwen.workflowEngine.common.bo.NodeDataBO;
import com.iyunwen.workflowEngine.common.dto.FlowWsDTO;
import com.iyunwen.workflowEngine.common.enums.NodeGroupParamKeyEnum;
import com.iyunwen.workflowEngine.graph.GraphEngine;
import com.iyunwen.workflowEngine.graph.GraphEngineFactory;
import com.iyunwen.workflowEngine.graph.model.GraphRunResult;
import com.iyunwen.workflowEngine.edges.EdgeBase;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DemoWorkflowService {

    public Map<String, Object> getParallelShowcaseDefinition() {
        FlowWsDTO workflow = buildParallelShowcaseWorkflow();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("project", "workflow-showcase-platform");
        response.put("scenario", "parallel-branch-join");
        response.put("workflow", workflow);
        response.put("highlights", List.of(
                "DAG-style branching from a single start node",
                "Parallel node execution on a dedicated engine thread pool",
                "Join/wait node that merges upstream branches before the end node"
        ));
        return response;
    }

    public Map<String, Object> runParallelShowcase() {
        FlowWsDTO workflow = buildParallelShowcaseWorkflow();
        InMemoryDemoCallback callback = new InMemoryDemoCallback();
        GraphEngine engine = GraphEngineFactory.getInstance()
                .getNewEngineInstanceByData("demo-user", workflow, true, callback);
        GraphRunResult runResult = engine.run(Map.of());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("project", "workflow-showcase-platform");
        response.put("workflowId", workflow.getId());
        response.put("workflowName", workflow.getName());
        response.put("status", runResult.getStatus().name());
        response.put("reason", runResult.getReason());
        response.put("stepsExecuted", engine.getMonitor().getDoCount());
        response.put("variables", engine.getGraphState().snapshotVariables());
        response.put("logs", callback.getNodeLogs());
        response.put("notes", List.of(
                "This public edition keeps the engine shape and execution model but replaces enterprise integrations with a local demo profile.",
                "The two output nodes intentionally sleep for different durations so parallel scheduling is visible in the event log."
        ));
        engine.close();
        return response;
    }

    private FlowWsDTO buildParallelShowcaseWorkflow() {
        FlowWsDTO workflow = new FlowWsDTO();
        workflow.setId("demo-parallel-showcase");
        workflow.setName("Parallel DAG Showcase");
        workflow.setDescription("A public demo that branches into two parallel output nodes, waits for both to finish, and then closes on an end node.");
        ViewPort viewPort = new ViewPort();
        viewPort.setX(0);
        viewPort.setY(0);
        viewPort.setZoom(0.85D);
        workflow.setViewport(viewPort);

        workflow.setNodes(List.of(
                node("start-1", NodeTypeEnum.START, "Start", Map.of(
                        NodeGroupParamKeyEnum.GUIDE_WORD.getKey(), "This demo focuses on the engine runtime rather than enterprise infrastructure.",
                        NodeGroupParamKeyEnum.GUIDE_QUESTION.getKey(), List.of("How does branching work?", "How are parallel nodes synchronized?")
                )),
                node("output-a", NodeTypeEnum.OUTPUT, "Parallel Branch A", Map.of(
                        NodeGroupParamKeyEnum.MESSAGE.getKey(), "Branch A produced a structured summary for the final report.",
                        "delay_ms", 450
                )),
                node("output-b", NodeTypeEnum.OUTPUT, "Parallel Branch B", Map.of(
                        NodeGroupParamKeyEnum.MESSAGE.getKey(), "Branch B prepared a document-parsing insight package.",
                        "delay_ms", 700
                )),
                node("runwait-1", NodeTypeEnum.RUNWAIT, "Join", Map.of()),
                node("end-1", NodeTypeEnum.END, "End", Map.of(
                        NodeGroupParamKeyEnum.OUTPUT_VARIABLE.getKey(), List.of(
                                Map.of("key", "branchA", "value", "output-a.message", "type", "ref"),
                                Map.of("key", "branchB", "value", "output-b.message", "type", "ref"),
                                Map.of("key", "showcase", "value", "parallel-branch-join", "type", "value")
                        )
                ))
        ));

        workflow.setEdges(List.of(
                edge("e-1", "start-1", "output-a"),
                edge("e-2", "start-1", "output-b"),
                edge("e-3", "output-a", "runwait-1"),
                edge("e-4", "output-b", "runwait-1"),
                edge("e-5", "runwait-1", "end-1")
        ));
        return workflow;
    }

    private NodeDataBO node(String id, NodeTypeEnum nodeType, String name, Map<String, Object> params) {
        BaseNodeData data = new BaseNodeData();
        data.setId(id);
        data.setType(nodeType.getType());
        data.setName(name);
        data.setDescription(name);

        NodeGroupParam groupParam = new NodeGroupParam();
        groupParam.setName("default");
        List<NodeParam> nodeParams = new ArrayList<>();
        params.forEach((key, value) -> {
            NodeParam param = new NodeParam();
            param.setKey(key);
            param.setValue(value);
            nodeParams.add(param);
        });
        groupParam.setParams(nodeParams);
        data.setGroupParams(List.of(groupParam));

        NodeDataBO nodeDataBO = new NodeDataBO();
        nodeDataBO.setId(id);
        nodeDataBO.setType(nodeType.getType());
        nodeDataBO.setData(data);
        return nodeDataBO;
    }

    private EdgeBase edge(String id, String source, String target) {
        EdgeBase edge = new EdgeBase();
        edge.setId(id);
        edge.setSource(source);
        edge.setTarget(target);
        edge.setSourceHandle(source + "->" + target);
        return edge;
    }
}
