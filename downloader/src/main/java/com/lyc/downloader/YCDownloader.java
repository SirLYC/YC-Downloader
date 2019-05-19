package com.lyc.downloader;

import android.content.Context;
import android.os.Looper;
import com.lyc.downloader.db.DownloadInfo;

import java.util.List;
import java.util.Map;

/**
 * @author liuyuchuan
 * @date 2019-05-10
 * @email kevinliu.sir@qq.com
 */
public abstract class YCDownloader {
    private static BaseServiceManager serviceManager;
    private static boolean installed;

    public static void install(Context context) {
        install(context, false);
    }

    public static void install(Context context, boolean multiProcess) {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            throw new UnsupportedOperationException("cannot install YCDownloader in worker thread");
        }
        if (installed) {
            throw new IllegalStateException("YCDownloader has been installed in this process!");
        }
        if (multiProcess) {
            serviceManager = new RemoteServiceManager(context);
        } else {
            serviceManager = new LocalServiceManager(context);
        }
        installed = true;
    }

    public static void registerDownloadListener(DownloadListener downloadListener) {
        serviceManager.registerDownloadListener(downloadListener);
    }

    public static void unregisterDownloadListener(DownloadListener downloadListener) {
        serviceManager.unregisterDownloadListener(downloadListener);
    }

    public static void startOrResume(long id) {
        serviceManager.startOrResume(id);
    }

    public static void pause(long id) {
        serviceManager.pause(id);
    }

    public static void startAll() {
        serviceManager.startAll();
    }

    public static void pauseAll() {
        serviceManager.pauseAll();
    }

    public static void cancel(long id) {
        serviceManager.cancel(id);
    }

    public static void submit(String url, String path, String filename, Map<String, String> customerHeaders, SubmitListener listener) {
        serviceManager.submit(url, path, filename, customerHeaders, listener);
    }

    public static DownloadInfo queryDownloadInfo(long id) {
        return serviceManager.queryDownloadInfo(id);
    }

    public static List<DownloadInfo> queryActiveDownloadInfoList() {
        return serviceManager.queryActiveDownloadInfoList();
    }

    public static List<DownloadInfo> queryDeletedDownloadInfoList() {
        return serviceManager.queryDeletedDownloadInfoList();
    }

    public static List<DownloadInfo> queryFinishedDownloadInfoList() {
        return serviceManager.queryFinishedDownloadInfoList();
    }
}
