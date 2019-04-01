package com.lyc.downloader;

import okhttp3.OkHttpClient;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
public class DownloadManager {

    // for http
    private OkHttpClient client;

    private static final class Holder {
        private static final DownloadManager instance = new DownloadManager();
    }
}
