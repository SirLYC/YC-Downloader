package com.lyc.downloader;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import com.lyc.downloader.db.DownloadInfo;
import com.lyc.downloader.utils.Logger;

import java.util.List;

/**
 * @author liuyuchuan
 * @date 2019-05-10
 * @email kevinliu.sir@qq.com
 */
public class RemoteDownloadService extends Service {

    public static final String TAG = "RemoteDownloadService";
    private static DownloadManager downloadManager;

    private final IDownloadService.Stub downloadService = new IDownloadService.Stub() {
        @Override
        public void registerDownloadCallback(IDownloadCallback callback) {
            downloadManager.setDownloadCallback(callback);
        }

        @Override
        public void unregisterDownloadCallback() {
            downloadManager.setDownloadCallback(null);
        }

        @Override
        public void registerDownloadTasksChangeCallback(IDownloadTasksChangeCallback callback) {
            downloadManager.setDownloadTasksChangeCallback(callback);
        }

        @Override
        public void unregisterDownloadTasksChangeCallback() {
            downloadManager.setDownloadTasksChangeCallback(null);
        }

        @Override
        public void submit(String url, String path, String filename, ISubmitCallback callback) {
            Logger.d("RemoteDownloadService", "submit " + url);
            downloadManager.submit(url, path, filename, callback);
        }

        @Override
        public DownloadInfo queryDownloadInfo(long id) {
            return downloadManager.queryDownloadInfo(id);
        }

        @Override
        public List<DownloadInfo> queryActiveDownloadInfoList() {
            return downloadManager.queryActiveDownloadInfoList();
        }

        @Override
        public List<DownloadInfo> queryDeletedDownloadInfoList() {
            return downloadManager.queryDeletedDownloadInfoList();
        }

        @Override
        public List<DownloadInfo> queryFinishedDownloadInfoList() {
            return downloadManager.queryFinishedDownloadInfoList();
        }

        @Override
        public void startAll() {
            downloadManager.startAll();
        }

        @Override
        public void pauseAll() {
            downloadManager.pauseAll();
        }

        @Override
        public void startOrResume(long id, boolean restart) {
            downloadManager.startOrResume(id, restart);
        }

        @Override
        public void pause(long id) {
            downloadManager.pause(id);
        }

        @Override
        public void cancel(long id) {
            downloadManager.cancel(id);
        }

        @Override
        public void delete(long id, boolean deleteFile) {
            downloadManager.delete(id, deleteFile);
        }

        @Override
        public int getMaxRunningTask() {
            return downloadManager.getMaxRunningTask();
        }

        @Override
        public void setMaxRunningTask(int count) {
            downloadManager.setMaxRunningTask(count);
        }

        @Override
        public boolean isAvoidFrameDrop() {
            return downloadManager.isAvoidFrameDrop();
        }

        @Override
        public void setAvoidFrameDrop(boolean avoidFrameDrop) {
            downloadManager.setAvoidFrameDrop(avoidFrameDrop);
        }

        @Override
        public long getSendMessageIntervalNanos() {
            return downloadManager.getSendMessageIntervalNanos();
        }

        @Override
        public void setSendMessageIntervalNanos(long time) {
            downloadManager.setSendMessageIntervalNanos(time);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        DownloadManager.init(getApplicationContext());
        downloadManager = DownloadManager.instance();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return downloadService.asBinder();
    }
}
