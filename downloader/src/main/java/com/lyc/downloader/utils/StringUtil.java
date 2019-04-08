package com.lyc.downloader.utils;

/**
 * @author liuyuchuan
 * @date 2019/4/8
 * @email kevinliu.sir@qq.com
 */
public class StringUtil {
    public static String bpsToString(double bps) {
        if (bps < 512) {
            return bps + "B/s";
        }

        bps /= 1024;

        if (bps < 512) {
            return bps + "KB/s";
        }

        bps /= 1024;

        if (bps < 512) {
            return bps + "MB/s";
        }

        bps /= 1024;

        // ....
        if (bps < 512) {
            return bps + "GB/s";
        }

        bps /= 1024;

        // ...
        // ...
        // ...
        return bps + "TB/s";
    }
}
