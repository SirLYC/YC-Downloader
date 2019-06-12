package com.lyc.downloader;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import com.lyc.downloader.utils.Logger;

/**
 * Created by Liu Yuchuan on 2019/5/19.
 */
class LocalServiceManager extends BaseServiceManager {

    LocalServiceManager(Context context) {
        super(context);
    }

    @Override
    void initServiceConnection() {
        downloadServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Logger.d("LocalServiceManager", "successful connect to downloadService");
                try {
                    service.linkToDeath(deathRecipient, 0);
                } catch (RemoteException e) {
                    Logger.e("LocalServiceManager", "cannot link to death", e);
                }
                downloadService = LocalDownloadService.asInterface(service);
                try {
                    downloadService.registerDownloadCallback(downloadListenerDispatcher);
                } catch (RemoteException e) {
                    Logger.e("LocalServiceManager", "cannot register downloadListenerDispatcher", e);
                }
                countDownLatch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                downloadService = null;
            }
        };
    }

    @Override
    void connectToService() {
        Intent intent = new Intent(appContext, LocalDownloadService.class);

        if (appContext.bindService(intent, downloadServiceConnection, Context.BIND_AUTO_CREATE)) {
            Logger.d("LocalServiceManager", "try to connect to download service");
        } else {
            Logger.d("LocalServiceManager", "no permission to download service");
        }

    }
}
