package com.itao.workflowEngine.common.enums;

public enum GlobalNodeParamKey {
    CURRENT_TIME("current_time"),
    CHAT_HISTORY("chat_history"),
    ACCESS_TOKEN("access_token"),
    PRESET_QUESTION("preset_question");

    private final String type;

    GlobalNodeParamKey(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
