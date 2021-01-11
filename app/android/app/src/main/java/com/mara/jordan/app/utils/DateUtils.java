package com.mara.jordan.app.utils;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class DateUtils {

    private static final long SECONDS_TO_MILLISECONDS = 1000;
    private static final DateFormat DATE_TIME_FORMAT = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
    private static final DateFormat TIME_FORMAT = DateFormat.getTimeInstance(DateFormat.MEDIUM);

//    static {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            TimeZone defaultTimezone = TimeZone.getTimeZone(ZoneId.systemDefault());
//            DATE_TIME_FORMAT.setTimeZone(defaultTimezone);
//            TIME_FORMAT.setTimeZone(defaultTimezone);
//        }
//    }
    /**
     *
     * @param time date to print/format, timestamp in seconds
     * @param alwaysPrintDay print date only if different from current date
     */
    public static String formatTimestamp(long time, boolean alwaysPrintDay){
        Date date = new Date(time * SECONDS_TO_MILLISECONDS);
        if(alwaysPrintDay || !isSameDay(date, new Date())) {
            return DATE_TIME_FORMAT.format(date);
        } else {
            return TIME_FORMAT.format(date);
        }
    }

    private static boolean isSameDay(Date d1, Date d2) {
        Calendar cal1 = new GregorianCalendar();
        cal1.setTime(d1);
        Calendar cal2 = new GregorianCalendar();
        cal2.setTime(d2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

}
