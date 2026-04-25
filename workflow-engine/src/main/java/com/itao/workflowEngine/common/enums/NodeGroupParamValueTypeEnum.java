package com.itao.workflowEngine.common.enums;

public enum NodeGroupParamValueTypeEnum {
    NODE_PARAM_VALUE_INPUT("value"),
    NODE_PARAM_VALUE_REF("ref");

    private final String type;

    NodeGroupParamValueTypeEnum(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
