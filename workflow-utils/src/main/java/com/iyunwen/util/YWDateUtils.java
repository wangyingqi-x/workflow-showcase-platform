package com.iyunwen.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class YWDateUtils {

    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private YWDateUtils() {
    }

    public static String dateFormat(Date date, String pattern) {
        return new SimpleDateFormat(pattern).format(date);
    }
}
