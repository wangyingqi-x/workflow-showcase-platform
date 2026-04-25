package com.itao.workflowEngine.nodes.basic.condition;

import com.itao.workflowEngine.callback.BaseCallback;
import com.itao.workflowEngine.common.BaseNodeData;
import com.itao.workflowEngine.edges.EdgeBase;
import com.itao.workflowEngine.graph.GraphState;
import com.itao.workflowEngine.nodes.BaseNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ConditionNode extends BaseNode {

    private List<ConditionRule> rules;
    private String defaultTarget;
    private List<String> nextNodeIds;

    public ConditionNode(BaseNodeData nodeData, String workflowId, String userId, GraphState graphState,
                         List<EdgeBase> targetEdges, List<EdgeBase> sourceEdges, int maxSteps, BaseCallback callback) {
        super(nodeData, workflowId, userId, graphState, targetEdges, sourceEdges, maxSteps, callback);
    }

    @Override
    protected void initData() {
        super.initData();
        rules = new ArrayList<>();
        nextNodeIds = new ArrayList<>();
        defaultTarget = stringValue(nodeParams.get("default_target"));
        Object rawRules = nodeParams.get("conditions");
        if (rawRules instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> rawMap) {
                    ConditionRule rule = ConditionRule.from(rawMap);
                    if (rule != null) {
                        rules.add(rule);
                    }
                }
            }
        }
    }

    @Override
    public Map<String, Object> _run(String uniqueId) {
        nextNodeIds.clear();
        ConditionRule matchedRule = null;
        for (ConditionRule rule : rules) {
            if (rule.matches(graphState)) {
                matchedRule = rule;
                if (!isBlank(rule.target())) {
                    nextNodeIds.add(rule.target());
                }
                break;
            }
        }
        if (nextNodeIds.isEmpty() && !isBlank(defaultTarget)) {
            nextNodeIds.add(defaultTarget);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", matchedRule != null);
        result.put("matched_rule", matchedRule == null ? "" : matchedRule.name());
        result.put("selected_targets", new ArrayList<>(nextNodeIds));
        result.put("default_target", defaultTarget);

        graphState.addAgentTrace(id, "condition",
                matchedRule == null ? "Condition fell back to default branch." : "Condition matched branch " + matchedRule.name() + ".",
                Map.of(
                        "selectedTargets", new ArrayList<>(nextNodeIds),
                        "matchedRule", matchedRule == null ? "" : matchedRule.name()
                ));
        return result;
    }

    @Override
    public List<String> routeNode() {
        return nextNodeIds.isEmpty() ? super.routeNode() : new ArrayList<>(nextNodeIds);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ConditionRule(String name, String source, String operator, Object value, String target) {

        static ConditionRule from(Map<?, ?> rawMap) {
            String source = stringValue(rawMap.get("source"));
            String target = stringValue(rawMap.get("target"));
            if (source.isBlank() || target.isBlank()) {
                return null;
            }
            String name = stringValue(rawMap.get("name"));
            String operator = stringValue(rawMap.get("operator"));
            return new ConditionRule(
                    name.isBlank() ? target : name,
                    source,
                    operator.isBlank() ? "contains" : operator.toLowerCase(),
                    rawMap.get("value"),
                    target
            );
        }

        boolean matches(GraphState graphState) {
            Object left = graphState.getVariableByStr(source);
            return switch (operator) {
                case "exists" -> left != null && !String.valueOf(left).isBlank();
                case "equals" -> Objects.equals(normalize(left), normalize(value));
                case "not_equals" -> !Objects.equals(normalize(left), normalize(value));
                case "greater_than" -> toDouble(left) > toDouble(value);
                case "less_than" -> toDouble(left) < toDouble(value);
                case "contains" -> String.valueOf(left).contains(String.valueOf(value));
                default -> false;
            };
        }

        private Object normalize(Object value) {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value == null) {
                return "";
            }
            String text = String.valueOf(value).trim();
            if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                return Boolean.parseBoolean(text);
            }
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return text;
            }
        }

        private double toDouble(Object value) {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (Exception ignored) {
                return 0D;
            }
        }

        private static String stringValue(Object value) {
            return value == null ? "" : String.valueOf(value).trim();
        }
    }
}
