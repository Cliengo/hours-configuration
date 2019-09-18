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

        // New datetime in the website timezone
        DateTime monday = new DateTime(websiteTimeZone)
                // Go to Monday
                .withDayOfWeek(DateTimeConstants.MONDAY)
                // At start of day
                .withTimeAtStartOfDay()
                // Convert to UTC
                .withZone(DateTimeZone.UTC);
        long mondayMillis = monday.getMillis();

        for (int i = 1; i <= 7; i++) {
            String entry = config.get(i-1);

            Matcher intervalMatcher = HoursConfiguration.INTERVALS_REGEX.matcher(entry);
            if (intervalMatcher.matches()) {
                // We have various intervals. Split by comma, get every interval, and add start and end of every interval (in UTC)
                for(String subEntry : entry.split(",")) {
                    Interval interval = HoursConfiguration.stringToInterval(subEntry, websiteTimeZone);
                    intervals.add(new Interval(
                            interval.getStart().withDayOfWeek(i).withZone(DateTimeZone.UTC),
                            interval.getEnd().withDayOfWeek(i).withZone(DateTimeZone.UTC))
                    );
                }

            } else if (HoursConfiguration.ALL_DAY_REGEX.matcher(entry).matches()) {
                // Add start of day and end of day
                DateTime start = new DateTime(websiteTimeZone).withDayOfWeek(i).withTimeAtStartOfDay().withZone(DateTimeZone.UTC),
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
            obj.addProperty(START, (start.getMillis() - mondayMillis) / 1000);
            obj.addProperty(END, (end.getMillis() - mondayMillis) / 1000);

            result.add(obj);
        }

        return result;
    }
}
