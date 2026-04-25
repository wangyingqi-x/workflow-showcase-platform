package com.itao.workflowEngine.nodes.basic.start;

import com.itao.util.DateFormatUtils;
import com.itao.workflowEngine.callback.BaseCallback;
import com.itao.workflowEngine.common.BaseNodeData;
import com.itao.workflowEngine.common.enums.GlobalNodeParamKey;
import com.itao.workflowEngine.common.enums.NodeGroupParamKeyEnum;
import com.itao.workflowEngine.common.enums.RoleType;
import com.itao.workflowEngine.edges.EdgeBase;
import com.itao.workflowEngine.graph.GraphState;
import com.itao.workflowEngine.nodes.BaseNode;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StartNode extends BaseNode {

    private String guideWord;
    private List<String> guideQuestions = new ArrayList<>();

    public StartNode(BaseNodeData nodeData, String workflowId, String userId, GraphState graphState,
                     List<EdgeBase> targetEdges, List<EdgeBase> sourceEdges, int maxSteps, BaseCallback callback) {
        super(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
    }

    @Override
    protected void initData() {
        super.initData();
        this.guideQuestions = new ArrayList<>();
        Object guideWordValue = nodeParams.get(NodeGroupParamKeyEnum.GUIDE_WORD.getKey());
        this.guideWord = guideWordValue == null ? null : String.valueOf(guideWordValue);
        Object guideQuestionValue = nodeParams.get(NodeGroupParamKeyEnum.GUIDE_QUESTION.getKey());
        if (guideQuestionValue instanceof List<?> list) {
            for (Object item : list) {
                guideQuestions.add(String.valueOf(item));
            }
        }
        graphState.setVariable(id, GlobalNodeParamKey.CURRENT_TIME.getType(), DateFormatUtils.dateFormat(new Date(), DateFormatUtils.DATE_TIME_FORMAT));
        graphState.setVariable(id, GlobalNodeParamKey.ACCESS_TOKEN.getType(), callback.getAccessToken());
    }

    @Override
    public Map<String, Object> _run(String uniqueId) {
        if (guideWord != null && !guideWord.isBlank()) {
            callback.onGuideWord(id, uniqueId, guideWord);
            graphState.saveContext(guideWord, RoleType.HUMAN);
        }
        if (!guideQuestions.isEmpty()) {
            callback.onGuideQuestion(id, uniqueId, guideQuestions);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(NodeGroupParamKeyEnum.GUIDE_WORD.getKey(), guideWord);
        result.put(NodeGroupParamKeyEnum.GUIDE_QUESTION.getKey(), new ArrayList<>(guideQuestions));
        return result;
    }
}
