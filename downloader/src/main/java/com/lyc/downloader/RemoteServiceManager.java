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
class RemoteServiceManager extends BaseServiceManager {
    private boolean inServerProcess;

    RemoteServiceManager(Context context) {
        super(context);
    }

    @Override
    void initServiceConnection() {
        downloadServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Logger.d("RemoteServiceManager", "connect to service success " + Thread.currentThread().getName());
                try {
                    service.linkToDeath(deathRecipient, 0);
                } catch (RemoteException e) {
                    Logger.e("RemoteServiceManager", "cannot link to death", e);
                }
                downloadService = IDownloadService.Stub.asInterface(service);
                registerLocalListeners();
                countDownLatch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                downloadService = null;
            }
        };
    }

    @Override
    public void connectToService() {
        String processName = getProcessName(appContext);
        if (processName.endsWith("yc_downloader")) {
            inServerProcess = true;
        } else {
            inServerProcess = false;
            Intent intent = new Intent(appContext, RemoteDownloadService.class);
            if (appContext.bindService(intent, downloadServiceConnection, Context.BIND_AUTO_CREATE)) {
                Logger.d("RemoteServiceManager", "try to connect to download service");
            } else {
                Logger.d("RemoteServiceManager", "no permission to download service");
            }
        }
    }

    @Override
    boolean isInServerProcess() {
        return inServerProcess;
    }
}
