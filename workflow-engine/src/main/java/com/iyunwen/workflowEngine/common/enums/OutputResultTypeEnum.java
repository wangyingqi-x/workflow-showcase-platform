package com.iyunwen.workflowEngine.common.enums;

public enum OutputResultTypeEnum {
    DEFAULT("default"),
    CHOOSE("choose"),
    INPUT("input");

    private final String value;

    OutputResultTypeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static boolean isDefaultType(String value) {
        return DEFAULT.value.equals(value);
    }
}
