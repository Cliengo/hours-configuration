package com.cliengo;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for frontend hours format.
 */
public class FrontendHoursFormatTest {
    @Test
    public void testConversion() {
        List<String> config = Arrays.asList(HoursConfiguration.copyForAllDays("09-17"));
        assertEquals(
                "Conversion to frontend hours format and backwards did not return same result",
                config,
                FrontendHoursFormat.toHoursConfig(FrontendHoursFormat.fromHoursConfig(config))
        );
    }
}
