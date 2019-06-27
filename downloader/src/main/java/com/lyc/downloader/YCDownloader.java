package com.lyc.downloader;

import android.Manifest;
import android.Manifest.permission;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import com.lyc.downloader.db.DownloadInfo;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author liuyuchuan
 * @date 2019-05-10
 * @email kevinliu.sir@qq.com
 * API of this library.
 * Note that all setXX or register/unregisterXX methods are executed in another thread, so that it won't block main thread.
 * And they are guarded to be executed after connected to service successfully.
 * However, all the get/isXX methods are executed in main thread immediately, witch means they won't wait for connection
 * to the service.
 * If you want to get the right value immediately, call them in {@link YCDownloader#postOnConnection(Runnable)}
 * All listener methods are guarded to call in main thread
 * @see DownloadListener
 * @see DownloadTasksChangeListener
 */
public abstract class YCDownloader {
    private static BaseServiceManager serviceManager;
    private static boolean installed;

    private static final String TAG = "YCDownloader";

    private static void checkInstall() {
        if (!installed) {
            throw new IllegalStateException("Cannot access to YCDownloader! Did you forget to install it first?");
        }
    }

    private static void checkPermissions(Context context) {
        String[] requiredPermissions = {Manifest.permission.INTERNET};

        boolean missingRequiredPermission = false;
        for (String requiredPermission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(context, requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                missingRequiredPermission = true;
                Log.e(TAG, "missing required permission: " + requiredPermission);
            }
        }

        if (missingRequiredPermission) {
            throw new IllegalStateException("Missing required permissions! Please check log and manifest.");
        }

        // these permissions is not required but important for downloader
        String[] importantPermissions = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                permission.READ_EXTERNAL_STORAGE
        };
        for (String importantPermission : importantPermissions) {
            if (ContextCompat.checkSelfPermission(context, importantPermission) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "missing important permission: " + importantPermission);
            }
        }
    }

    /*--------------------------------------- install ---------------------------------------*/
    public static void install(Context context) {
        install(context, false);
    }

    /**
     * install downloader in current process
     *
     * @param context cannot be null; and it should be related to application context
     */
    public static void install(Context context, boolean multiProcess) {
        if (installed) {
            return;
        }
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            throw new UnsupportedOperationException("cannot install YCDownloader in worker thread");
        }

        checkPermissions(context);

        if (multiProcess) {
            serviceManager = new RemoteServiceManager(context);
        } else {
            serviceManager = new LocalServiceManager(context);
        }
        installed = true;
    }

    /*---------------------------------------- api ----------------------------------------*/

    /**
     * register download listener for all tasks
     * the listeners are guarded not add repeatedly to task with the same id
     */
    public static void registerDownloadListener(DownloadListener downloadListener) {
        serviceManager.registerDownloadListener((Long) null, downloadListener);
    }

    /**
     * register download listener for task with id provided by parameter
     *
     * @param id the task id to listen
     */
    public static void registerDownloadListener(long id, DownloadListener downloadListener) {
        serviceManager.registerDownloadListener(id, downloadListener);
    }

    /**
     * register download listener for task with id set provided by parameter
     *
     * @param ids the task ids to listen. if ids contains null, all the other ids will be ignored and register for all id
     */
    public static void registerDownloadListener(Set<Long> ids, DownloadListener downloadListener) {
        serviceManager.registerDownloadListener(ids, downloadListener);
    }


    /**
     * unregister download listener for all tasks
     * this operation will iterate all id's listener set
     * if you just want to unregister listener for specified id,
     * use {@link YCDownloader#unregisterDownloadListener(long, DownloadListener)}
     */
    public static void unregisterDownloadListener(DownloadListener downloadListener) {
        serviceManager.unregisterDownloadListener(downloadListener);
    }

    public static void unregisterDownloadListener(long id, DownloadListener downloadListener) {
        serviceManager.unregisterDownloadListener(id, downloadListener);
    }

    public static void unregisterDownloadListener(Set<Long> ids, DownloadListener downloadListener) {
        serviceManager.unregisterDownloadListener(ids, downloadListener);
    }

    /**
     * start a task
     * if the task is non-start-able now, the method do nothing
     * when will the task be non-start-able?
     * - already started: state is RUNNING, WAITING or CONNECTING
     * - busy changing state: STOPPING
     * - record deleted from db or id not exist
     *
     * @param id      download task id
     * @param restart if true restart the task will be restarted
     */
    public static void startOrResume(long id, boolean restart) {
        serviceManager.startOrResume(id, restart);
    }

    /**
     * pause the task is it's running
     *
     * @param id task id
     */
    public static void pause(long id) {
        serviceManager.pause(id);
    }

    /**
     * start all tasks that are not in running or preparing state
     */
    public static void startAll() {
        serviceManager.startAll();
    }

    public static void pauseAll() {
        serviceManager.pauseAll();
    }

    /**
     * cancel a task and delete it from {@link DownloadManager}
     * it's info will still be in database and can be recovered by
     * {@link #startOrResume(long, boolean)}
     *
     * @param id task id
     */
    public static void cancel(long id) {
        serviceManager.cancel(id);
    }

    /**
     * submit a task to downloader service
     *
     * @param url      Download url, only HTTP or HTTPS supported for now.
     * @param path     Parent directory to save the downloaded file
     * @param filename Downloaded filename, nullable. If it's null, the name will be decided by downloader
     *                 service and inform caller by {@link DownloadListener}
     * @param listener the listener inform caller the result of this commit.
     */
    public static void submit(String url, String path, String filename, SubmitListener listener) {
        serviceManager.submit(url, path, filename, new ISubmitCallback.Stub() {
            @Override
            public void submitSuccess(DownloadInfo downloadInfo) {
                DownloadExecutors.androidMain.execute(() -> listener.submitSuccess(downloadInfo));
            }

            @Override
            public void submitFail(String reason) {
                DownloadExecutors.androidMain.execute(() -> listener.submitFail(new Exception(reason)));
            }
        });
    }

    /**
     * delete this download task from db
     * the download tmp file will be deleted anyway
     *
     * @param id         task id
     * @param deleteFile download file if it's finished
     */
    public static void delete(long id, boolean deleteFile) {
        serviceManager.delete(id, deleteFile);
    }

    /*---------------------  query sould be called in worker thread! --------------------- */
    @WorkerThread
    public static DownloadInfo queryDownloadInfo(long id) {
        return serviceManager.queryDownloadInfo(id);
    }

    /**
     * state != FINISH && state != CANCELLED
     */
    @WorkerThread
    public static List<DownloadInfo> queryActiveDownloadInfoList() {
        return serviceManager.queryActiveDownloadInfoList();
    }

    /**
     * state == CANCELLED
     */
    @WorkerThread
    public static List<DownloadInfo> queryDeletedDownloadInfoList() {
        return serviceManager.queryDeletedDownloadInfoList();
    }

    /**
     * state == FINISH
     */
    @WorkerThread
    public static List<DownloadInfo> queryFinishedDownloadInfoList() {
        return serviceManager.queryFinishedDownloadInfoList();
    }

    public static long getSpeedLimit() {
        return serviceManager.getSpeedLimit();
    }

    /**
     * Limit download speed.
     *
     * @param speedLimit in bytes / second
     */
    public static void setSpeedLimit(long speedLimit) {
        serviceManager.setSpeedLimit(speedLimit);
    }

    public static int getMaxRunningTask() {
        return serviceManager.getMaxRunningTask();
    }

    /**
     * Note: count >= 0 and count <= {@link #getMaxSupportRunningTask()}
     * If count >= 0 and count != current maxRunningTask,
     * schedule tasks
     */
    public static void setMaxRunningTask(int count) {
        serviceManager.setMaxRunningTask(count);
    }


    public static int getMaxSupportRunningTask() {
        return serviceManager.getMaxSupportRunningTask();
    }

    public static boolean isAvoidFrameDrop() {
        return serviceManager.isAvoidFrameDrop();
    }

    /**
     * @param avoidFrameDrop if avoidFrameDrop {@link DownloadListener#onDownloadProgressUpdate(long, long, long, double)}
     *                       will be called with certain interval to avoid send to many message to
     *                       main thread
     */
    public static void setAvoidFrameDrop(boolean avoidFrameDrop) {
        serviceManager.setAvoidFrameDrop(avoidFrameDrop);
    }

    /**
     * interval invalid when {@link #isAvoidFrameDrop() returns true}
     */
    public static void setSendMessageInterval(long time, TimeUnit timeUnit) {
        serviceManager.setSendMessageIntervalNanos(timeUnit.toNanos(time));
    }

    public static long getSendMessageIntervalNanos() {
        return serviceManager.getSendMessageIntervalNanos();
    }

    /**
     * post a runnable to execute when the download service is connected
     *
     * @param runnable the command to execute
     */
    public static void postOnConnection(Runnable runnable) {
        serviceManager.postOnConnection(runnable);
    }

    /**
     * @see DownloadError#translator
     * This function is not an IPC call.
     * If you install downloader in multi-process mode
     * and want your translator to get work, call this method in the place where all
     * the process will called (suck as static block, Application's methods, singleInstance's methods...).
     * For default Translator implementation
     * @see DownloadError
     */
    public static void setErrorTranslator(DownloadError.Translator translator) {
        if (translator == null) return;
        DownloadError.instance().setTranslator(translator);
    }

    public static String translateErrorCode(int code) {
        return DownloadError.instance().translate(code);
    }

    /**
     * @see DownloadTasksChangeListener
     */
    public static void registerDownloadTasksChangeListener(DownloadTasksChangeListener downloadTasksChangeListener) {
        serviceManager.registerDownloadTasksChangeListener(downloadTasksChangeListener);
    }

    /**
     * @see DownloadTasksChangeListener
     */
    public static void removeDownloadTasksChangeListener(DownloadTasksChangeListener downloadTasksChangeListener) {
        serviceManager.removeDownloadTasksChangeListener(downloadTasksChangeListener);
    }

    public static boolean isInServerProcess() {
        return serviceManager.isInServerProcess();
    }
}
