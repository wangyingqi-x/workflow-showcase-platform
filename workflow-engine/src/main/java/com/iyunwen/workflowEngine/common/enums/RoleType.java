package com.iyunwen.workflowEngine.common.enums;

public enum RoleType {
    HUMAN("human"),
    AI("ai");

    private final String type;

    RoleType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
