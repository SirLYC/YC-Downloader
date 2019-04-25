package com.lyc.downloader.utils;

import java.text.DecimalFormat;

/**
 * @author liuyuchuan
 * @date 2019/4/8
 * @email kevinliu.sir@qq.com
 */
public class StringUtil {
    private static final DecimalFormat df = new DecimalFormat("#.##");


    public static String bpsToString(double bps) {
        if (bps < 512) {
            return df.format(bps) + "B/s";
        }

        bps /= 1024;

        if (bps < 512) {
            return df.format(bps) + "KB/s";
        }

        bps /= 1024;

        if (bps < 512) {
            return df.format(bps) + "MB/s";
        }

        bps /= 1024;

        // ....
        if (bps < 512) {
            return df.format(bps) + "GB/s";
        }

        bps /= 1024;

        // ...
        // ...
        // ...
        return df.format(bps) + "TB/s";
    }

    public static String byteToString(double b) {
        if (b < 512) {
            return df.format(b) + "B";
        }

        b /= 1024;

        if (b < 512) {
            return df.format(b) + "KB";
        }

        b /= 1024;

        if (b < 512) {
            return df.format(b) + "MB";
        }

        b /= 1024;

        if (b < 512) {
            return df.format(b) + "GB";
        }

        b /= 1024;

        return df.format(b) + "TB";
    }
}
