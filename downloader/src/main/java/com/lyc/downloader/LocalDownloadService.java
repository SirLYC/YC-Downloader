package com.lyc.downloader;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.annotation.Nullable;
import com.lyc.downloader.db.DownloadInfo;

import java.util.List;

/**
 * Created by Liu Yuchuan on 2019/5/19.
 */
public class LocalDownloadService extends Service {
    private LocalDownloadServiceBinder binder = new LocalDownloadServiceBinder();
    private DownloadManager downloadManager;

    public static IDownloadService asInterface(IBinder binder) {
        if (!(binder instanceof LocalDownloadServiceBinder)) {
            throw new IllegalArgumentException("only local download service binder can be transferred to download service!");
        }
        return (IDownloadService) binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        DownloadManager.init(this);
        downloadManager = DownloadManager.instance();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    class LocalDownloadServiceBinder extends Binder implements IDownloadService {
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
        public long getSpeedLimit() throws RemoteException {
            return downloadManager.getSpeedLimit();
        }

        @Override
        public void setSpeedLimit(long speedLimit) throws RemoteException {
            downloadManager.setSpeedLimit(speedLimit);
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

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}
