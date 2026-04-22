package com.iyunwen.workflowEngine.graph.thread;

import com.iyunwen.util.LogMdcUtil;
import com.iyunwen.workflowEngine.graph.model.NodeRunResult;
import com.iyunwen.workflowEngine.graph.model.enums.NodeRunStatus;
import com.iyunwen.workflowEngine.nodes.BaseNode;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.Callable;

import org.springframework.context.i18n.LocaleContextHolder;

public class RunGraphNodeTask implements Callable<NodeRunResult> {

    private final BaseNode node;
    private final Locale locale;

    public RunGraphNodeTask(BaseNode node, Locale locale) {
        this.node = node;
        this.locale = locale;
    }

    @Override
    public NodeRunResult call() {
        try {
            LogMdcUtil.putChatId(node.getCallback().getChatId());
            LogMdcUtil.putWebId(node.getCallback().getWebId());
            LocaleContextHolder.setLocale(locale);
            node.run(new HashMap<>());
            return new NodeRunResult(node.getId(), NodeRunStatus.COMPLETED, "completed");
        } catch (Exception ex) {
            return new NodeRunResult(node.getId(), NodeRunStatus.FAILED, ex.getMessage());
        } finally {
            LogMdcUtil.clear();
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
