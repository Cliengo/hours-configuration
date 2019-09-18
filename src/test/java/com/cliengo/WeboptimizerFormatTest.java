package com.cliengo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for weboptimizer format.
 */
public class WeboptimizerFormatTest {
    @Test
    public void testConversion() {
        List<String> config = Arrays.asList(HoursConfiguration.copyForAllDays("09-17"));
        JsonArray weboptimizerFormat = WeboptimizerFormat.fromHoursConfig(config, DateTimeZone.forOffsetHours(-3));

        Assert.assertEquals("Number of entries does not match", config.size(), weboptimizerFormat.size());
        for (int i = 0; i < 7; i++) {
            JsonObject entry = weboptimizerFormat.get(i).getAsJsonObject();
            Assert.assertEquals("Start diff does not match for day #"+(i+1), entry.get("start").getAsLong(), ((i*24)+9)*60*60);
            Assert.assertEquals("End diff does not match for day #"+(i+1), entry.get("end").getAsLong(), ((i*24)+17)*60*60);
        }
    }
}
