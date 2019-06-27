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
        StringBuilder sb = new StringBuilder(url);

        parseFilenameFromUrlInner(sb);

        String result = sb.toString().replaceAll("\\s", "");
        try {
            result = URLDecoder.decode(sb.toString(), "utf-8");
        } catch (UnsupportedEncodingException e) {
            Logger.e("DownloadStringUtil", "parseFilenameFromUrl(" + result + ")", e);
        }

        int length = result.length();
        if (length > 127) {
            result = result.substring(length - 127, length);
        }
        return result;
    }

    private static void parseFilenameFromUrlInner(StringBuilder sb) {
        if (sb.length() == 0) {
            return;
        }

        removeUrlArgs(sb);
        int s = sb.length();
        if (sb.charAt(s - 1) == '/') {
            sb.delete(s - 1, s);
        }

        int index = sb.lastIndexOf("/");
        if (index != -1) {
            sb.delete(0, index + 1);
        }

        s = sb.length();
        while (sb.charAt(s - 1) == File.separatorChar) {
            sb.delete(s - 1, 1);
            s = sb.length();
        }

        if (s > 8 && "https://".equals(sb.substring(0, 8))) {
            sb.delete(0, 8);
        } else if (s > 6 && "http://".equals(sb.substring(0, 6))) {
            sb.delete(0, 6);
        } else {
            for (index = 0, s = sb.length(); index < s; index++) {
                if (sb.charAt(index) == File.separatorChar) {
                    sb.setCharAt(index, '_');
                }
            }
        }
    }

    private static void removeUrlArgs(StringBuilder url) {
        int index = url.indexOf("?");
        if (index != -1) {
            url.delete(index, url.length());
        }
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
