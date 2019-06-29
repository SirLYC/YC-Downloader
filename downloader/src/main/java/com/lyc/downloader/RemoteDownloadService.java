package com.lyc.downloader;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;

/**
 * @author liuyuchuan
 * @date 2019-05-10
 * @email kevinliu.sir@qq.com
 */
public class RemoteDownloadService extends Service {

    private IDownloadService.Stub downloadService;

    @Override
    public void onCreate() {
        super.onCreate();
        downloadService = new IDownloadService$StubImp();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return downloadService.asBinder();
    }
}
