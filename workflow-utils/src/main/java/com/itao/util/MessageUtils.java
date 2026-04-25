package com.itao.util;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MessageUtils {

    private static final Map<String, String> DEFAULT_MESSAGES = new HashMap<>();

    static {
        DEFAULT_MESSAGES.put("Graph.engine.noStartNode", "No start node found in workflow definition.");
        DEFAULT_MESSAGES.put("GraphEngine.max.steps.reached", "Workflow execution exceeded the configured step limit.");
        DEFAULT_MESSAGES.put("GraphEngine.stopped.by.user", "Workflow execution was stopped.");
        DEFAULT_MESSAGES.put("OutputNode.message.empty.default", "Empty output message");
        DEFAULT_MESSAGES.put("NodeFactory.nodeType.not.support", "Unsupported node type: {0}");
        DEFAULT_MESSAGES.put("NodeFactory.Node.creator.notFound", "No node creator registered for type: {0}");
        DEFAULT_MESSAGES.put("Graph.engine.invalid", "Invalid workflow graph.");
        DEFAULT_MESSAGES.put("BaseNode.run.stop.user", "Node execution was stopped.");
        DEFAULT_MESSAGES.put("BaseNode.run.over.maxStep", "Node {0} exceeded its max step limit {1}.");
    }

    private MessageUtils() {
    }

    public static String getMsg(String key) {
        return getMsg(key, null);
    }

    public static String getMsg(String key, Object[] args) {
        MessageSource messageSource = null;
        try {
            messageSource = com.itao.util.spring.SpringBeanProvider.getBean(MessageSource.class);
        } catch (Exception ignored) {
        }

        String pattern = DEFAULT_MESSAGES.getOrDefault(key, key);
        Locale locale = LocaleContextHolder.getLocale();
        if (messageSource != null) {
            pattern = messageSource.getMessage(key, args, pattern, locale == null ? Locale.getDefault() : locale);
        }
        return args == null || args.length == 0 ? pattern : MessageFormat.format(pattern, args);
    }
}
