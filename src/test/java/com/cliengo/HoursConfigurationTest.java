package com.cliengo;

import static com.cliengo.TestUtils.doWithFrozenTime;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.Arrays;

/**
 * Unit tests for hours configuration.
 */
public class HoursConfigurationTest {
    @Test
    public void testDisabled() {
        assertFalse("All-day disabled configuration returned true, should always return false",
                HoursConfiguration.isAllowedNow("-"));
    }

    @Test
    public void testAllDay() {
        assertTrue("All-day enabled configuration returned false, should always return true",
                HoursConfiguration.isAllowedNow("*"));
    }

    @Test
    public void testInvalidRegexes() {
        assertFalse("Configuration of invalid length considered valid, 7-day configuration should be required",
                HoursConfiguration.isValid(new String[3]));

        assertFalse("Invalid configuration considered valid, should always return false by default",
                HoursConfiguration.isValid(""));

        assertFalse("Invalid configuration considered valid, should always return false by default",
                HoursConfiguration.isValid("hello-world"));

        assertFalse("Configuration with leading space was considered valid",
                HoursConfiguration.isValid(" *"));

        assertFalse("Configuration with 1 digit was considered valid, 2 digits always required",
                HoursConfiguration.isValid("0-23"));

        assertFalse("Configuration with invalid separator was considered valid",
                HoursConfiguration.isValid("00:23"));

        assertFalse("Configuration with invalid separator was considered valid",
                HoursConfiguration.isValid("00-20;20-23"));

        assertFalse("Configuration with inverted separators was considered valid",
                HoursConfiguration.isValid("00,20-20,23"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEndBeforeStart() {
        HoursConfiguration.isAllowedNow("18-02");
    }

    @Test()
    public void testMidnight() {
        doWithFrozenTime(12, () -> {
            assertTrue(HoursConfiguration.isAllowedNow("00-00")); // Equivalent to *
            assertTrue(HoursConfiguration.isAllowedNow("00-24")); // Equivalent to *
            assertTrue(HoursConfiguration.isAllowedNow("12-00"));
        });
    }

    @Test
    public void testSingleDayWithinInterval() {
        doWithFrozenTime(12, () -> {
            String config = "09-10,11-14,18-21";
            assertTrue(String.format("Valid single-day configuration %s was not allowed", config),
                    HoursConfiguration.isAllowedNow(config));
        });
    }

    @Test
    public void testEdgeCases() {
        doWithFrozenTime(23, () -> {
            String config = "23-00";
            assertTrue(String.format("Valid single-day configuration %s was not allowed", config),
                    HoursConfiguration.isAllowedNow(config));

            config = "00-23";
            assertTrue(String.format("Valid single-day configuration %s was not allowed", config),
                    HoursConfiguration.isAllowedNow(config));
        });
    }

    @Test
    public void testWithinInterval() {
        doWithFrozenTime(12, () -> {
            String singleDayConfig = "11-13";
            String[] allWeekConfig = new String[7];
            Arrays.fill(allWeekConfig, singleDayConfig);

            assertTrue(String.format("Valid configuration %s was not allowed", Arrays.toString(allWeekConfig)),
                    HoursConfiguration.isAllowedNow(allWeekConfig));
        });
    }

    @Test
    public void testSingleDayNotWithinInterval() {
        final int hour = 12;
        doWithFrozenTime(hour, () -> {
            String singleDayConfig = "10-11,13-15";
            assertFalse(String.format("Config %s excludes %d but was allowed", singleDayConfig, hour),
                    HoursConfiguration.isAllowedNow(singleDayConfig));
        });
    }

    @Test
    public void testTimezone() {
        final DateTimeZone serverTimezone = DateTimeZone.forOffsetHours(-3),
                websiteTimezone = DateTimeZone.UTC;
        final int hour = 12; // 12 ART = 15 UTC
        String singleDayConfig = "14-18";
        doWithFrozenTime(new DateTime(serverTimezone).withHourOfDay(hour), () ->
                assertTrue(String.format("Config %s in timezone %s was not allowed within time %d in timezone %s",singleDayConfig, websiteTimezone.toString(), hour, serverTimezone),
                        HoursConfiguration.isAllowedNow(singleDayConfig, websiteTimezone)));
    }

    @Test
    public void testNotAllowedTimezone() {
        final DateTimeZone serverTimezone = DateTimeZone.forOffsetHours(-3),
                websiteTimezone = DateTimeZone.UTC;
        final int hour = 10; // 10 ART = 13 UTC
        String singleDayConfig = "10-12";
        doWithFrozenTime(new DateTime(serverTimezone).withHourOfDay(hour), () ->
                assertFalse(String.format("Config %s in timezone %s was allowed outside of time %d in timezone %s", singleDayConfig, websiteTimezone.toString(), hour, serverTimezone),
                        HoursConfiguration.isAllowedNow(singleDayConfig, websiteTimezone)));
    }

    @Test
    public void testMinutes() {
        String singleDayConfig = "10:37-12:45";
        DateTime test1 = new DateTime().withHourOfDay(10).withMinuteOfHour(37);
        doWithFrozenTime(test1, () ->
                assertTrue(String.format("Config %s includes %s but was not allowed", singleDayConfig, test1), HoursConfiguration.isAllowedNow(singleDayConfig))
        );
        DateTime test2 = new DateTime().withHourOfDay(12).withMinuteOfHour(45);
        doWithFrozenTime(test2, () ->
                assertTrue(String.format("Config %s includes %s but was not allowed", singleDayConfig, test2), HoursConfiguration.isAllowedNow(singleDayConfig))
        );
        DateTime test3 = test2.plusMinutes(1);
        doWithFrozenTime(test3, () ->
                assertFalse(String.format("Config %s excludes %s but was allowed", singleDayConfig, test3), HoursConfiguration.isAllowedNow(singleDayConfig))
        );
        DateTime test4 = new DateTime().withHourOfDay(10).withMinuteOfHour(0);
        doWithFrozenTime(test4, () ->
                assertFalse(String.format("Config %s excludes %s but was allowed", singleDayConfig, test4), HoursConfiguration.isAllowedNow(singleDayConfig))
        );
    }
}
