package com.itao.workflowEngine.common.enums;

public enum NodeGroupParamKeyEnum {
    GUIDE_WORD("guide_word"),
    GUIDE_QUESTION("guide_question"),
    MESSAGE("message"),
    OUTPUT_RESULT("output_result"),
    OUTPUT_VARIABLE("output_variable"),
    NODE_RUN_STATUS("node_run_status");

    private final String key;

    NodeGroupParamKeyEnum(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
