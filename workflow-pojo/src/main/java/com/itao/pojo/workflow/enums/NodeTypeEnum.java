package com.itao.pojo.workflow.enums;

public enum NodeTypeEnum {
    START(1, "start"),
    END(2, "end"),
    INPUT(3, "input"),
    OUTPUT(4, "output"),
    AGENT(5, "agent"),
    TOOL(6, "tool"),
    CONDITION(7, "condition"),
    WORKFLOW(17, "workflow"),
    RUNWAIT(21, "runwait");

    private final Integer value;
    private final String type;

    NodeTypeEnum(Integer value, String type) {
        this.value = value;
        this.type = type;
    }

    public Integer getValue() {
        return value;
    }

    public String getType() {
        return type;
    }

    public static NodeTypeEnum fromValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (NodeTypeEnum candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        return null;
    }

    public static NodeTypeEnum fromString(String type) {
        if (type == null) {
            return null;
        }
        for (NodeTypeEnum candidate : values()) {
            if (candidate.type.equalsIgnoreCase(type)) {
                return candidate;
            }
        }
        return null;
    }
}
