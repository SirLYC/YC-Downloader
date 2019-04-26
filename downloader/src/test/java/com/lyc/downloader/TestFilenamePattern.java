package com.lyc.downloader;

import org.junit.Assert;
import org.junit.Test;

import java.util.regex.Matcher;

/**
 * Created by Liu Yuchuan on 2019/4/26.
 */
public class TestFilenamePattern {
    @Test
    public void test() {
        String input = "aabb(12)";
        Matcher matcher = DownloadTask.reduplicatedFilenamePattern.matcher(input);
        Assert.assertTrue(matcher.find());
        Assert.assertEquals(2, matcher.groupCount());
        Assert.assertEquals("aabb", matcher.group(1));
        Assert.assertEquals("12", matcher.group(2));
    }
}
