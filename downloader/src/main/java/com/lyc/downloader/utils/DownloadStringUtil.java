package com.lyc.downloader.utils;

import com.lyc.downloader.Constants;
import org.greenrobot.greendao.annotation.NotNull;

import java.text.DecimalFormat;

/**
 * @author liuyuchuan
 * @date 2019/4/8
 * @email kevinliu.sir@qq.com
 */
public class DownloadStringUtil {
    private static final DecimalFormat df = new DecimalFormat("#.#");

    public static String bpsToString(double bps) {
        if (bps < 1024) {
            return df.format(bps) + "B/s";
        }

        bps /= 1024;

        if (bps < 1024) {
            return df.format(bps) + "KB/s";
        }

        bps /= 1024;

        if (bps < 1024) {
            return df.format(bps) + "MB/s";
        }

        bps /= 1024;

        // ....
        if (bps < 1024) {
            return df.format(bps) + "GB/s";
        }

        bps /= 1024;

        // ...
        // ...
        // ...
        return df.format(bps) + "TB/s";
    }

    public static String byteToString(double b) {
        if (b < 1024) {
            return df.format(b) + "B";
        }

        b /= 1024;

        if (b < 1024) {
            return df.format(b) + "KB";
        }

        b /= 1024;

        if (b < 1024) {
            return df.format(b) + "MB";
        }

        b /= 1024;

        if (b < 1024) {
            return df.format(b) + "GB";
        }

        b /= 1024;

        return df.format(b) + "TB";
    }

    @NotNull
    public static String parseFilenameFromUrl(String url) {
        String result = parseFilenameFromUrlInner(url);
        if (result.isEmpty()) {
            return Constants.UNKNOWN_FILE_NAME;
        }
        if (result.length() > 127) {
            int length = result.length();
            result = result.substring(length - 127, length);
        }
        return result;
    }

    private static String parseFilenameFromUrlInner(String url) {
        if (url == null) return "";
        int index = url.lastIndexOf("?");
        if (index != -1) url = url.substring(0, index);
        index = url.lastIndexOf("/");
        if (index != -1)
            return url.substring(index + 1);

        return url.replaceAll("/", "");
    }

    public static String parseFilenameFromContentDisposition(String value) {
        if (value == null || (value = value.trim()).isEmpty()) return null;
        int index = value.indexOf("filename=");
        if (index != -1) {
            return value.substring(index + 1).trim();
        }
        index = value.indexOf("name=");
        if (index != -1) {
            return value.substring(index).trim();
        }
        return null;
    }
}
