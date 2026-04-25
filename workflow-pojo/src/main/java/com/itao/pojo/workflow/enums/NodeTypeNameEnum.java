package com.itao.pojo.workflow.enums;

public enum NodeTypeNameEnum {
    OUTPUT("Output"),
    REPORT("Report"),
    UNKNOWN("Unknown");

    private final String displayName;

    NodeTypeNameEnum(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
