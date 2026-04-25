package com.itao.util;

import org.slf4j.MDC;

public final class LogMdcUtil {

    private static final String TRACE_ID = "traceId";
    private static final String WEB_ID = "webId";
    private static final String CHAT_ID = "chatId";

    private LogMdcUtil() {
    }

    public static void putTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(TRACE_ID, traceId);
        }
    }

    public static void putWebId(long webId) {
        MDC.put(WEB_ID, String.valueOf(webId));
    }

    public static void putChatId(String chatId) {
        if (chatId != null) {
            MDC.put(CHAT_ID, chatId);
        }
    }

    public static void clear() {
        MDC.clear();
    }
}
