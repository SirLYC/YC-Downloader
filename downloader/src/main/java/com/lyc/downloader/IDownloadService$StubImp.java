package com.lyc.downloader;

import com.lyc.downloader.db.DownloadInfo;

import java.util.List;

/**
 * @author liuyuchuan
 * @date 2019-06-28
 * @email kevinliu.sir@qq.com
 */
public final class IDownloadService$StubImp extends IDownloadService.Stub {

    private final DownloadManager downloadManager = DownloadManager.instance();

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
    public void setMaxRunningTask(int count) {
        downloadManager.setMaxRunningTask(count);
    }

    @Override
    public long getSpeedLimit() {
        return downloadManager.getSpeedLimit();
    }

    @Override
    public void setSpeedLimit(long speedLimit) {
        downloadManager.setSpeedLimit(speedLimit);
    }

    @Override
    public boolean isAllowDownload() {
        return downloadManager.isAllowDownload();
    }

    @Override
    public void setAllowDownload(boolean allowDownload) {
        downloadManager.setAllowDownload(allowDownload);
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
}
