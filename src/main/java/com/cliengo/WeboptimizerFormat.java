package com.cliengo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Hours configuration format used by Weboptimizer. This class serves to transform between it and
 * {@link HoursConfiguration}.  The Weboptimizer format recognized by this class has the following shape:
 * <pre>
 * [
 *   {
 *     "start": int, // In seconds since Monday at 00:00:00
 *     "end": int,   // In seconds since Monday at 00:00:00
 *   },
 *   ...
 * ]
 * </pre>
 */
public class WeboptimizerFormat {
    private static final String START = "start", END = "end";

    /**
     * Convert a <b>valid</b> hours configuration to Weboptimizer format.
     *
     * @param config          Configuration to convert.
     * @param websiteTimeZone Website timezone.
     * @return JSON array with converted format, as documented for this class.
     * @throws IllegalArgumentException On invalid configuration.
     * @throws IllegalStateException On illegal state, e.g. configuration is valid but no matchers match.
     */
    public static JsonArray fromHoursConfig(List<String> config, DateTimeZone websiteTimeZone) throws IllegalArgumentException, IllegalStateException {
        HoursConfiguration.validate(config);

        List<Interval> intervals = new ArrayList<>();

        for (int i = 1; i <= 7; i++) {
            String entry = config.get(i-1);

            Matcher intervalMatcher = HoursConfiguration.INTERVALS_REGEX.matcher(entry);
            if (intervalMatcher.matches()) {
                // We have various intervals. Split by comma, get every interval, and add start and end of every interval (in UTC)
                for(String subEntry : entry.split(",")) {
                    Interval interval = HoursConfiguration.stringToInterval(subEntry, websiteTimeZone);
                    // Move intervals to the day of week we're currently iterating over
                    DateTime movedStart = interval.getStart().withDayOfWeek(i),
                            movedEnd = interval.getEnd().withDayOfWeek(i);
                    // If interval ends in 00, move it one day further like #stringToInterval does
                    if (interval.getEnd().getDayOfWeek() > interval.getStart().getDayOfWeek()) {
                        movedEnd = movedEnd.plusDays(1);
                    }

                    intervals.add(new Interval(
                            movedStart,
                            movedEnd
                    ));
                }
            } else if (HoursConfiguration.ALL_DAY_REGEX.matcher(entry).matches()) {
                // Add start of day and end of day
                DateTime start = new DateTime(websiteTimeZone).withDayOfWeek(i).withTimeAtStartOfDay(),
                        end = start.plusDays(1).minusSeconds(1);
                intervals.add(new Interval(start, end));
            } else if (HoursConfiguration.DISABLED_DAY_REGEX.matcher(entry).matches()) {
                // Do nothing
            } else {
                throw new IllegalStateException("Hours configuration is valid but no known matcher matches");
            }
        }

        JsonArray result = new JsonArray();
        for (Interval interval : intervals) {
            JsonObject obj = new JsonObject();
            obj.addProperty(START, getWeekTimestamp(interval.getStart()));
            obj.addProperty(END, getWeekTimestamp(interval.getEnd(), true));
            result.add(obj);
        }

        return result;
    }

    /**
     * Equivalent to {@code precdingMonday(now, false)}.
     *
     * @see #precedingMonday(DateTime, boolean)
     */
    public static DateTime precedingMonday(DateTime now) {
        return precedingMonday(now, false);
    }

    /**
     * Get a {@link DateTime} corresponding to the Monday immediately preceding the specified date-time, at midnight.
     *
     * @param now Reference time.
     * @param goBackMondayMidnight Whether to go one week back if now is Monday at midnight.
     * @return Monday immediately preceding the reference time at midnight.
     */
    public static DateTime precedingMonday(DateTime now, boolean goBackMondayMidnight) {
        DateTime result = now
                // Go to previous Monday
                .withDayOfWeek(DateTimeConstants.MONDAY)
                // At start of day
                .withTimeAtStartOfDay();

        if (goBackMondayMidnight && now.getDayOfWeek() == DateTimeConstants.MONDAY && now.getMillisOfDay() == 0) {
            result = result.minusDays(7);
        }
        return result;
    }

    /**
     * Equivalent to {@code getWeekTimestamp(now, false)}.
     */
    public static long getWeekTimestamp(DateTime now) {
        return getWeekTimestamp(now, false);
    }

    /**
     * Get the "week timestamp" for the given date, ie. the number of <b>seconds</b> (not millis) between the given
     * date-time and the start of the Monday immediately preceding it.
     *
     * @param now The reference time.
     * @param goBackMondayMidnight Whether to calculate a timestamp from a previous monday if now is Monday at midnight.
     * @return The number of seconds since the Monday immediately before the reference time.
     * @see #precedingMonday(DateTime, boolean)
     */
    public static long getWeekTimestamp(DateTime now, boolean goBackMondayMidnight) {
        return (now.getMillis() - precedingMonday(now, goBackMondayMidnight).getMillis()) / 1000;
    }
}
