package com.lyc.downloader;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import com.lyc.downloader.utils.Logger;

import java.util.concurrent.CountDownLatch;

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
                try {
                    connectLock.lock();
                    try {
                        service.linkToDeath(deathRecipient, 0);
                    } catch (RemoteException e) {
                        Logger.e("RemoteServiceManager", "cannot link to death", e);
                    }
                    downloadService = LocalDownloadService.asInterface(service);
                    try {
                        downloadService.registerDownloadCallback(downloadListenerDispatcher);
                    } catch (RemoteException e) {
                        Logger.e("RemoteServiceManager", "cannot register downloadListenerDispatcher", e);
                    }
                    countDownLatch.countDown();
                } finally {
                    connectLock.unlock();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                try {
                    connectLock.lock();
                    downloadService = null;
                } finally {
                    connectLock.unlock();
                }
            }
        };
    }

    @Override
    void connectToService() {
        try {
            connectLock.lock();
            countDownLatch = new CountDownLatch(1);
            Intent intent = new Intent(appContext, LocalDownloadService.class);
            System.out.println("downloadServiceConnection: " + downloadServiceConnection);
            appContext.bindService(intent, downloadServiceConnection, Context.BIND_AUTO_CREATE);
        } finally {
            connectLock.unlock();
        }
    }
}
