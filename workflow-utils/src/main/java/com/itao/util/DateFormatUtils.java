package com.itao.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class DateFormatUtils {

    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private DateFormatUtils() {
    }

    public static String dateFormat(Date date, String pattern) {
        return new SimpleDateFormat(pattern).format(date);
    }
}
