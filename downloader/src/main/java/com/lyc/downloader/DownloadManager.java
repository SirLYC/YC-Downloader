package com.lyc.downloader;

import android.content.Context;
import okhttp3.OkHttpClient;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
public class DownloadManager {

    // for http
    private OkHttpClient client;

    private static Context appContext;

    public static void init(Context context) {
        if (context == null) {
            throw new NullPointerException("Context cannot be null!");
        }
        appContext = context.getApplicationContext();
    }

    private static final class Holder {
        private static final DownloadManager instance = new DownloadManager();
    }
}
