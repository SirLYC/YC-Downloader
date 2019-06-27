package com.lyc.downloader.utils;

import org.greenrobot.greendao.annotation.NotNull;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
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

        try {
            return URLDecoder.decode(result, "utf-8");
        } catch (UnsupportedEncodingException e) {
            Logger.e("DownloadStringUtil", "parseFilenameFromUrl(" + result + ")", e);
        }

        if (result.length() > 127) {
            int length = result.length();
            result = result.substring(length - 127, length);
        }
        return result;
    }

    private static String parseFilenameFromUrlInner(String url) {
        if (url == null) return "";
        int index = url.indexOf("name=");

        if (index != -1) {
            return removeUrlArgs(url.substring(index + 5).trim());
        }

        index = url.indexOf("filename=");
        if (index != -1) {
            return removeUrlArgs(url.substring(index + 9).trim());
        }

        index = url.indexOf("file=");
        if (index != -1) {
            return removeUrlArgs(url.substring(index + 5).trim());
        }

        url = removeUrlArgs(url);
        index = url.lastIndexOf("/");
        String tmp;
        if (index != -1 && !(tmp = url.substring(index + 1).trim()).isEmpty()) {
            url = tmp;
        }

        if (url.endsWith("_")) {
            url = url.substring(0, url.length() - 1);
        }

        return url.replaceAll("https://", "")
                .replaceAll("http://", "")
                .replaceAll(File.separator, "_");
    }

    private static String removeUrlArgs(String url) {
        int index = url.indexOf("?");
        if (index != -1) return url.substring(0, index);
        return url;
    }

    public static String parseFilenameFromContentDisposition(String value) {
        if (value == null || (value = value.trim()).isEmpty()) return null;
        int index = value.indexOf("filename=");

        String processName = null;

        if (index != -1) {
            processName = value.substring(index + 9).trim();
        } else if ((index = value.indexOf("name=")) != -1) {
            processName = value.substring(index + 5).trim();
        }

        if (processName != null && !processName.isEmpty()) {
            try {
                return URLDecoder.decode(processName, "utf-8");
            } catch (UnsupportedEncodingException e) {
                Logger.e("DownloadStringUtil", "try to decode url string: " + processName, e);
                return processName;
            }
        }

        return null;
    }
}
