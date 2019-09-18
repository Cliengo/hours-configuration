package com.cliengo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeField;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class used to standardize configurations or anything else that might involve hour intervals, and that is
 * checked against the current datetime to see whether the configuration allows for a particular action to be performed.
 * Allowed hour intervals should be saved following the regexes defined here:
 * <ul>
 *     <li>{@link #INTERVALS_REGEX}</li>
 *     <li>{@link #ALL_DAY_REGEX}</li>
 *     <li>{@link #DISABLED_DAY_REGEX}</li>
 * </ul>
 * in a 7-item String array, where index 0 corresponds to Monday, 1 to Tuesday, etc. (this is to use {@link DateTime#getDayOfWeek()} - 1
 * to match the rules for the current day). Alternatively, a single configuration can be used which will be assumed is
 * for the current day.
 */
@SuppressWarnings("WeakerAccess")
public class HoursConfiguration {

    /**
     * Single interval regex. Specifies start and end hours and optionally minutes.  Each hour and minute is a 2-digit
     * number, valid hours are 00-24 and valid minutes are 00-59.
     */
    public static final Pattern SINGLE_INTERVAL_REGEX = Pattern.compile("(\\d{2})(:(\\d{2}))?-(\\d{2})(:(\\d{2}))?");
    /**
     * Actual regex used to match enabled hours since it supports multiple comma-separated intervals. Valid examples:
     * <ul>
     *     <li>09-17</li>
     *     <li>09-13,15-18</li>
     *     <li>09-17,18-21,22-23</li>
     *     <li>09-13,22-00</li>
     *     <li>09:00-13:15,22:03-00</li>
     *     <li>09-13:18,15:21-18</li>
     * </ul>
     *
     * @see #SINGLE_INTERVAL_REGEX
     */
    public static final Pattern INTERVALS_REGEX = Pattern.compile("^" + SINGLE_INTERVAL_REGEX.toString() + "(," + SINGLE_INTERVAL_REGEX.toString() + ")*$");
    /**
     * Regex for notifications enabled all 24 hours.
     */
    public static final Pattern ALL_DAY_REGEX = Pattern.compile("^\\*$");
    /**
     * Regex for notifications disabled all 24 hours.
     */
    public static final Pattern DISABLED_DAY_REGEX = Pattern.compile("^-$");

    /**
     * Equivalent to {@link #isAllowedNow(List, DateTimeZone)} in the current timezone.
     */
    public static boolean isAllowedNow(List<String> configuration) throws IllegalArgumentException, IllegalStateException {
        return isAllowedNow(configuration, null);
    }

    /**
     * Equivalent to {@link #isAllowedNow(String, DateTimeZone)} for the current day (in the specified timezone).
     *
     * @param configuration Complete hours configuration. Should be a 7-element list, one entry for each day.
     * @param timeZone      (Optional) Timezone in which to evaluate. Null means current timezone.
     * @return Whether the configuration allows an action to be performed now.
     * @throws IllegalArgumentException If {@code configuration.size() != 7} or if {@link #isAllowedNow(String)} throws.
     * @throws IllegalStateException    If {@link #isAllowedNow(String)} throws.
     */
    public static boolean isAllowedNow(List<String> configuration, DateTimeZone timeZone) throws IllegalArgumentException, IllegalStateException {
        if (configuration.size() != 7) {
            throw new IllegalArgumentException("Provided configuration should have exactly 7 entries, one for each day, but has " + configuration.size());
        }
        return isAllowedNow(configuration.get(getCurrentDayIndex(timeZone)), timeZone);
    }

    /**
     * Equivalent to {@link #isAllowedNow(String[], DateTimeZone)} in the current timezone.
     */
    public static boolean isAllowedNow(String[] configuration) throws IllegalArgumentException, IllegalStateException {
        return isAllowedNow(configuration, null);
    }

    /**
     * Equivalent to {@link #isAllowedNow(String, DateTimeZone)} for the current day (in the specified timezone).
     *
     * @param configuration Complete hours configuration. Should be a 7-item array, one entry for each day.
     * @param timeZone      (Optional) Timezone in which to evaluate. Null means current timezone.
     * @return Whether the configuration allows an action to be performed now.
     * @throws IllegalArgumentException If {@code configuration.length != 7} or if {@link #isAllowedNow(String)} throws.
     * @throws IllegalStateException    If {@link #isAllowedNow(String)} throws.
     */
    public static boolean isAllowedNow(String[] configuration, DateTimeZone timeZone) throws IllegalArgumentException, IllegalStateException {
        if (configuration.length != 7) {
            throw new IllegalArgumentException("Provided configuration should have exactly 7 entries, one for each day, but has " + configuration.length);
        }
        return isAllowedNow(configuration[getCurrentDayIndex(timeZone)], timeZone);
    }

    /**
     * Equivalent to {@link #isAllowedNow(String, DateTimeZone)} in the current timezone.
     */
    public static boolean isAllowedNow(String currentDayRules) throws IllegalStateException, IllegalArgumentException {
        return isAllowedNow(currentDayRules, null);
    }

    /**
     * Checks whether the configuration allows an action to be performed now.
     *
     * @param currentDayRules Configuration for the current day.
     * @param timeZone        (Optional) Timezone in which to evaluate. Null means current timezone.
     * @return Whether the configuration allows an action to be performed now.
     * @throws IllegalStateException    On regex errors.
     * @throws IllegalArgumentException If a valid interval ends before it starts (eg. 18-02).
     */
    public static boolean isAllowedNow(String currentDayRules, DateTimeZone timeZone) throws IllegalStateException, IllegalArgumentException {
        DateTime now = new DateTime(timeZone);
        Matcher intervalsMatcher = INTERVALS_REGEX.matcher(currentDayRules),
                allDayMatcher = ALL_DAY_REGEX.matcher(currentDayRules),
                disabledDayMatcher = DISABLED_DAY_REGEX.matcher(currentDayRules);

        validate(currentDayRules);

        if (intervalsMatcher.matches()) {
            String[] intervals = currentDayRules.split(",");
            return Arrays.stream(intervals).anyMatch(config -> {
                Interval interval = stringToInterval(config, timeZone);
                // #contains is exclusive in the end, but our configurations are inclusive, so add 1 minute to include it
                return interval.withEnd(interval.getEnd().plusMinutes(1)).contains(now);
            });
        } else if (allDayMatcher.matches()) {
            return true;
        } else if (disabledDayMatcher.matches()) {
            return false;
        } else {
            System.err.format("Day rules %s don't match any valid regex, assuming not allowed\n", currentDayRules);
            return false;
        }
    }

    /**
     * Checks whether the specified configuration is valid.  Differs from {@link #validate(String)} in that no exception
     * is thrown if the configuration is invalid, only false is returned.
     *
     * @param singleDayConfig Single-day configuration.
     * @return Whether the configuration is valid.
     */
    public static boolean isValid(String singleDayConfig) {
        try {
            validate(singleDayConfig);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Equivalent to calling {@link #isValid(String)} for all elements of the specified array.
     *
     * @param configuration Configuration. Should be a 7-element array.
     * @return Whether all configurations are valid.
     */
    public static boolean isValid(String[] configuration) {
        return configuration.length == 7 && Arrays.stream(configuration).allMatch(HoursConfiguration::isValid);
    }

    /**
     * Equivalent to {@link #isValid(String[])} for collections.
     *
     * @param configuration Configuration. Should be a 7-element collection.
     * @return Whether all configurations are valid.
     */
    public static boolean isValid(Collection<String> configuration) {
        return configuration.size() == 7 && configuration.stream().allMatch(HoursConfiguration::isValid);
    }

    /**
     * Returns a valid hours configuration that is the same for all days of the week.
     *
     * @param singleDayConfig Single-day configuration.
     * @return The configuration repeated for all days of the week.
     * @throws IllegalArgumentException If configuration is invalid.
     */
    public static String[] copyForAllDays(String singleDayConfig) throws IllegalArgumentException {
        validate(singleDayConfig);
        String[] result = new String[7];
        Arrays.fill(result, singleDayConfig);
        return result;
    }

    /**
     * Returns a valid hours configuration that is the same for all weekdays, and is disabled for weekend days.
     *
     * @param weekdayConfig Weekday configuration.
     * @return The configuration repeated for all weekdays, and disabled for weekend days.
     * @throws IllegalArgumentException If configuration is invalid.
     */
    public static String[] copyForWeekdays(String weekdayConfig) throws IllegalArgumentException {
        validate(weekdayConfig);
        String[] result = copyForAllDays(weekdayConfig);
        result[5] = "-";
        result[6] = "-";

        return result;
    }

    /**
     * Get the current day index. Uses JodaTime methods to follow their standard and not have to reinvent the wheel.
     *
     * @return The current day index, as per {@link DateTime#get(DateTimeField)}, <b>minus 1</b>.
     * @param timeZone Timezone in which to evaluate.
     */
    private static int getCurrentDayIndex(DateTimeZone timeZone) {
        DateTime now = new DateTime(timeZone);
        return now.getDayOfWeek() - 1;
    }

    /**
     * Validate configuration length and then call {@link #validate(String)} for each entry.
     *
     * @param configuration Configuration to validate.
     * @throws IllegalArgumentException On invalid configuration.
     * @see #validate(String)
     */
    public static void validate(String[] configuration) throws IllegalArgumentException {
        if (configuration.length != 7) {
            throw new IllegalArgumentException("Configuration length must be exactly 7, one entry for each day");
        }
        Arrays.stream(configuration).forEach(HoursConfiguration::validate);
    }

    /**
     * Equivalent to {@link #validate(String[])} for collections instead of arrays.
     *
     * @param configuration Configuration to validate.
     * @throws IllegalArgumentException On invalid configuration.
     * @see #validate(String)
     */
    public static void validate(Collection<String> configuration) throws IllegalArgumentException {
        if (configuration.size() != 7) {
            throw new IllegalArgumentException("Configuration length must be exactly 7, one entry for each day");
        }
        configuration.forEach(HoursConfiguration::validate);
    }

    /**
     * Validates a single-day configuration and throws exception detailing error for invalid configurations.
     *
     * @param singleDayConfig Single-day configuration
     * @throws IllegalArgumentException On invalid configurations.
     * @throws IllegalStateException    On regex errors. This exception should <b>NOT</b> be thrown under normal circumstances.
     */
    public static void validate(String singleDayConfig) throws IllegalArgumentException, IllegalStateException {
        if (INTERVALS_REGEX.matcher(singleDayConfig).matches()) {
            String[] chunks = singleDayConfig.split(",");
            if (chunks.length == 0) {
                throw new IllegalArgumentException("Empty configuration");
            } else if (chunks.length > 1) {
                Arrays.stream(singleDayConfig.split(",")).forEach(HoursConfiguration::validate);
            } else {
                // The following throws if end is before start
                stringToInterval(chunks[0]); // Timezone doesn't matter here, start and end are assumed to be in the same timezone
            }
        } else if (!DISABLED_DAY_REGEX.matcher(singleDayConfig).matches() && !ALL_DAY_REGEX.matcher(singleDayConfig).matches()) {
            throw new IllegalArgumentException("Malformed hours configuration " + singleDayConfig);
        }
    }

    /**
     * Equivalent to {@link #stringToInterval(String, DateTimeZone)} with the current timezone.
     */
    public static Interval stringToInterval(String interval) throws IllegalArgumentException {
        return stringToInterval(interval, null);
    }

    /**
     * Parse a valid interval string and return a matching {@link Interval} that represents it.
     *
     * @param interval Interval to parse
     * @param timeZone (Optional) Interval timezone. Null means current timezone.
     * @return The corresponding interval
     * @throws IllegalArgumentException If interval string is invalid
     */
    public static Interval stringToInterval(String interval, DateTimeZone timeZone) throws IllegalArgumentException {
        Matcher intervalMatcher = SINGLE_INTERVAL_REGEX.matcher(interval);
        // find() or matches() must be called on a matcher before using group() and related functions
        if (!intervalMatcher.matches()) {
            throw new IllegalArgumentException("Regex was validated but does not match single-interval regex");
        }
        int startHour = Integer.parseInt(intervalMatcher.group(1)),
                startMinutes = Optional.ofNullable(intervalMatcher.group(3)).map(Integer::parseInt).orElse(0),
                endHour = Integer.parseInt(intervalMatcher.group(4)),
                endMinutes = Optional.ofNullable(intervalMatcher.group(6)).map(Integer::parseInt).orElse(0);
        // JodaTime doesn't support 24, map it back to 0
        if (endHour == 24) {
            endHour = 0;
        }
        // withTimeAtStartOfDay sets minutes, seconds, milliseconds, etc. to 0; then we change the hour to what we want
        DateTime start = new DateTime(timeZone).withTimeAtStartOfDay().withHourOfDay(startHour).withMinuteOfHour(startMinutes),
                end = new DateTime(timeZone).withTimeAtStartOfDay().withHourOfDay(endHour).withMinuteOfHour(endMinutes);

        // Special case: consider 24 or 00 as the start of the next day
        if (endHour == 0) {
            if (endMinutes != 0) {
                throw new IllegalArgumentException("End minutes may not be non-zero if end hour is 24 or 00 for interval " + interval);
            }
            end = end.withTimeAtStartOfDay().plusDays(1);
        }
        return new Interval(start, end);
    }

    /**
     * Inverse of {@link #stringToInterval(String)}.
     *
     * @param interval Interval to convert to string.
     * @return The corresponding string.
     */
    public static String intervalToString(Interval interval) {
        StringBuilder result = new StringBuilder();
        DateTime start = interval.getStart(), end = interval.getEnd();

        result.append(String.format("%02d", start.getHourOfDay()));
        if (start.getMinuteOfHour() != 0) {
            result.append(String.format(":%02d", start.getMinuteOfHour()));
        }
        result.append("-");
        result.append(String.format("%02d", end.getHourOfDay()));
        if (end.getMinuteOfHour() != 0) {
            result.append(String.format(":%02d", end.getMinuteOfHour()));
        }
        return result.toString();
    }

}
