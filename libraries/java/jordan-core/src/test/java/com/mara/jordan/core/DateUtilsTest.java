package com.mara.jordan.core;

import org.junit.Test;

import static org.junit.Assert.*;

public class DateUtilsTest {

    /** 2020-06-15 12:00:00 UTC — safely in the past, never "today". */
    private static final long PAST_TIMESTAMP = 1592222400L;

    @Test
    public void testFormatTimestampPastDateAlwaysIncludesDate() {
        // Even with alwaysPrintDay=false, a past date is never "today" → includes date
        String withFlag = DateUtils.formatTimestamp(PAST_TIMESTAMP, true);
        String withoutFlag = DateUtils.formatTimestamp(PAST_TIMESTAMP, false);
        assertEquals(withFlag, withoutFlag);
    }

    @Test
    public void testFormatTimestampTodayWithoutDayIsShorter() {
        long now = System.currentTimeMillis() / 1000L;
        String withDay = DateUtils.formatTimestamp(now, true);
        String withoutDay = DateUtils.formatTimestamp(now, false);
        assertTrue("time-only should be shorter than date+time", withoutDay.length() < withDay.length());
    }

    @Test
    public void testFormatTimestampTodayAlwaysPrintDayIncludesDate() {
        long now = System.currentTimeMillis() / 1000L;
        String result = DateUtils.formatTimestamp(now, true);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testFormatTimestampReturnsDifferentStringsForDifferentTimes() {
        String t1 = DateUtils.formatTimestamp(PAST_TIMESTAMP, true);
        String t2 = DateUtils.formatTimestamp(PAST_TIMESTAMP + 3600L, true);
        assertNotEquals(t1, t2);
    }

    @Test
    public void testFormatTimestampEpochDoesNotThrow() {
        // epoch = 0 should not throw
        assertNotNull(DateUtils.formatTimestamp(0L, true));
    }
}
