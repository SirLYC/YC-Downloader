package com.lyc.downloader;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;

/**
 * Created by Liu Yuchuan on 2019/5/19.
 */
public class LocalDownloadService extends Service {
    private IDownloadService$StubImp binder;

    public static IDownloadService asInterface(IBinder binder) {
        if (!(binder instanceof IDownloadService$StubImp)) {
            throw new IllegalArgumentException("only IDownloadService$StubImp can be transferred to download service!");
        }
        return (IDownloadService) binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        binder = new IDownloadService$StubImp();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
