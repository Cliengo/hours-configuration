package com.cliengo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * Hours configuration format used by frontend. This class serves to transform between it and
 * {@link HoursConfiguration}.  The frontend JSON format recognized by this class has the following shape:
 * <pre>
 * [
 *   {
 *     "days": [int array],    // Where 1 is Monday, 2 Tuesday, etc.
 *     "range": "start-end|*", // Corresponding to either {@link HoursConfiguration#SINGLE_INTERVAL_REGEX} or {@link HoursConfiguration#ALL_DAY_REGEX}
 *   },
 *   ...
 * ]
 * </pre>
 */
public class FrontendHoursFormat {
    private static final String DAYS = "days", RANGE = "range";

    /**
     * Convert frontend hours format to a valid {@link HoursConfiguration} format.
     *
     * @param hours Hours JSON, in the format described in this class' documentation,
     * @return The <b>valid</b> matching hours config.
     * @throws IllegalArgumentException On malformed JSON or invalid hours configuration.
     */
    public static List<String> toHoursConfig(JsonArray hours) throws IllegalArgumentException {
        // 7-element list with nulls
        List<List<Interval>> entries = new ArrayList<>(7);
        entries.addAll(Collections.nCopies(7, null));

        hours.forEach(entry -> {
            if (!entry.getAsJsonObject().has(DAYS)) {
                throw new IllegalArgumentException("Malformed hours, no " + DAYS + " key");
            }
            if (!entry.getAsJsonObject().has(RANGE)) {
                throw new IllegalArgumentException("Malformed hours, no " + RANGE + " key");
            }

            // Get start and end times for current entry
            Interval interval;
            String range = entry.getAsJsonObject().get(RANGE).getAsString();
            Matcher intervalMatcher = HoursConfiguration.SINGLE_INTERVAL_REGEX.matcher(range);
            if (HoursConfiguration.ALL_DAY_REGEX.matcher(range).matches()) {
                interval = new Interval(
                        new DateTime().withTimeAtStartOfDay(),
                        new DateTime().millisOfDay().withMaximumValue() // End of day, see https://stackoverflow.com/a/32134093
                );
            } else if (intervalMatcher.matches()) {
                interval = HoursConfiguration.stringToInterval(range);
            } else {
                throw new IllegalArgumentException("Malformed range " + range);
            }

            // Add range to all corresponding days
            JsonArray days = entry.getAsJsonObject().getAsJsonArray(DAYS);
            days.forEach(d -> {
                int day = d.getAsInt() - 1; // 1 is Monday in JSON => 0 is Monday in `ranges`
                List<Interval> existingRanges = entries.get(day);
                if (existingRanges == null) {
                    entries.set(day, new ArrayList<>(Collections.singletonList(interval)));
                } else {
                    // Check for overlapping ranges
                    List<Interval> overlappingIntervals = existingRanges.stream().filter(interval::overlaps).collect(Collectors.toList());
                    if (!overlappingIntervals.isEmpty()) {
                        throw new IllegalArgumentException(String.format("Overlapping ranges: %s is included within %s", HoursConfiguration.intervalToString(interval), overlappingIntervals.stream().map(HoursConfiguration::intervalToString).collect(Collectors.joining(","))));
                    }

                    // No overlap, add new range
                    existingRanges.add(interval);
                }
            });
        });

        // Convert to valid hours configuration
        List<String> result = entries.stream().map(intervals -> {
            StringBuilder entry = new StringBuilder();
            if (intervals == null) {
                entry.append("-");
            } else if (intervals.get(0).getStart().getHourOfDay() == 0 && intervals.get(0).getEndMillis() == intervals.get(0).getEnd().millisOfDay().withMaximumValue().getMillis()) {
                // TODO I think this is not necessary as overlap would have been detected previously
                if (intervals.size() > 2) {
                    throw new IllegalStateException("Parsing a 00-24 range but there are more ranges, only 00-24 should be present in " + intervals);
                }
                entry.append("*");
            } else {
                entry.append(intervals.stream().map(HoursConfiguration::intervalToString).collect(Collectors.joining(",")));
            }
            return entry.toString();
        }).collect(Collectors.toList());
        HoursConfiguration.validate(result); // Result must be valid
        return result;
    }

    /**
     * Convert a valid {@link HoursConfiguration} format to frontend hours format.
     *
     * @param config Valid hours configuration.
     * @return The <b>valid</b> matching hours config.
     * @throws IllegalArgumentException On malformed hours configuration.
     */
    public static JsonArray fromHoursConfig(List<String> config) {
        HoursConfiguration.validate(config);
        // Group by ranges, adapted from https://stackoverflow.com/a/41095159
        Map<String, List<Integer>> groupedRanges = new HashMap<>();
        for (int i = 0; i < config.size(); i++) {
            int finalI = i;
            Arrays.stream(config.get(i).split(",")).forEach(range -> groupedRanges.computeIfAbsent(range, c -> new ArrayList<>()).add(finalI + 1));
        }

        // Convert to properly formatted JSON object
        JsonArray result = new JsonArray();
        groupedRanges.forEach((range, days) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty(RANGE, range);

            JsonArray entryDays = new JsonArray();
            days.forEach(entryDays::add);
            entry.add(DAYS, entryDays);

            result.add(entry);
        });
        return result;
    }
}
