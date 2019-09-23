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
                    intervals.add(new Interval(
                            interval.getStart().withDayOfWeek(i),
                            interval.getEnd().withDayOfWeek(i))
                    );
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
            DateTime start = interval.getStart(), end = interval.getEnd();
            JsonObject obj = new JsonObject();
            obj.addProperty(START, getWeekTimestamp(start));
            obj.addProperty(END, getWeekTimestamp(end));
            result.add(obj);
        }

        return result;
    }

    /**
     * Get a {@link DateTime} corresponding to the Monday immediately preceding the specified date-time, at midnight.
     *
     * @param now Reference time.
     * @return Monday immediately preceding the reference time at midnight.
     */
    public static DateTime precedingMonday(DateTime now) {
        return now
                // Go to previous Monday
                .withDayOfWeek(DateTimeConstants.MONDAY)
                // At start of day
                .withTimeAtStartOfDay();
    }

    /**
     * Get the "week timestamp" for the given date, ie. the number of <b>seconds</b> (not millis) between the given
     * date-time and the start of the Monday immediately preceding it.
     *
     * @param now The reference time.
     * @return The number of seconds since the Monday immediately before the reference time.
     */
    public static long getWeekTimestamp(DateTime now) {
        return (now.getMillis() - precedingMonday(now).getMillis()) / 1000;
    }
}
