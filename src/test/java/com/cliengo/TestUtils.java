package com.cliengo;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;

class TestUtils {
    /**
     * Equivalent to {@link #doWithFrozenTime(DateTime, Runnable)} with default DateTime on current one
     */
    static void doWithFrozenTime(Runnable test) {
        doWithFrozenTime(DateTime.now(), test);
    }

    /**
     * Equivalent to {@link #doWithFrozenTime(DateTime, Runnable)} with a DateTime that corresponds to the given hour.
     */
    static void doWithFrozenTime(int hour, Runnable test) {
        doWithFrozenTime(new DateTime().withTimeAtStartOfDay().withHourOfDay(hour), test);
    }

    /**
     * Runs the given runnable (note: <b>not</b> in a separate thread) with a fixed {@link DateTime} (effectively freezing
     * time for the runnable). Unfreezes time after completion. No big deal.
     *
     * @param setTime Set time to maintain for the runnable
     * @param test    Test runnable to run with frozen time.
     */
    static void doWithFrozenTime(DateTime setTime, Runnable test) {
        try {
            // Freeze
            DateTimeUtils.setCurrentMillisFixed(setTime.getMillis());
            test.run();
        } finally {
            // Unfreeze
            DateTimeUtils.setCurrentMillisSystem();
        }
    }

    /**
     * Get a week timestamp.
     *
     * @param day     Day of week. <b>Monday is 1</b>.
     * @param hour    Hours.
     * @param minutes Minutes.
     * @return The computed week timestamp.
     */
    static int weekTimestamp(int day, int hour, int minutes) {
        return ((day-1)*24*60 + hour*60 + minutes) * 60;
    }
}
