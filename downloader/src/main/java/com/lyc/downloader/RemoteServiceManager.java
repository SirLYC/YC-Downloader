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
class RemoteServiceManager extends BaseServiceManager {
    private boolean inServerProcess;

    public RemoteServiceManager(Context context) {
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
                    downloadService = IDownloadService.Stub.asInterface(service);
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
    public void connectToService() {
        try {
            connectLock.lock();
            countDownLatch = new CountDownLatch(1);
            String processName = getProcessName(appContext);
            if (processName.endsWith("yc_downloader")) {
                inServerProcess = true;
            } else {
                inServerProcess = false;
                Intent intent = new Intent(appContext, RemoteDownloadService.class);
                appContext.bindService(intent, downloadServiceConnection, Context.BIND_AUTO_CREATE);
            }
        } finally {
            connectLock.unlock();
        }
    }
}
