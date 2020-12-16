package com.mara.jordan.app.utils;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class DateUtils {

    /**
     *
     * @param time date to print/format
     * @param alwaysPrintDay print date only if different from current date
     */
    public static String formatTimestamp(long time, boolean alwaysPrintDay){
        Date date = new Date(time);
        if(alwaysPrintDay || !isSameDay(date, new Date())) {
            return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.MEDIUM).format(date);
        } else {
            return DateFormat.getTimeInstance(DateFormat.MEDIUM).format(date);
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
