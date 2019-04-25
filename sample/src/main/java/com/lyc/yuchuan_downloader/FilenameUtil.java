package com.lyc.yuchuan_downloader;

/**
 * @author liuyuchuan
 * @date 2019-04-24
 * @email kevinliu.sir@qq.com
 */
public class FilenameUtil {
    public static String parseFileName(String url) {
        int index = url.lastIndexOf("?");
        if (index != -1) url = url.substring(0, index);
        index = url.lastIndexOf("/");
        if (index != -1)
            return url.substring(index + 1);

        return url;
    }
}
