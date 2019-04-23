package com.lyc.downloader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.lyc.downloader.db.DaoMaster;
import com.lyc.downloader.db.DaoMaster.DevOpenHelper;
import com.lyc.downloader.db.DaoSession;
import okhttp3.OkHttpClient;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
public class DownloadManager {

    static final String DB_NAME = "yuchuan_downloader_db";
    @SuppressLint("StaticFieldLeak")
    private volatile static DownloadManager instance;
    // for http
    final OkHttpClient client;
    final DaoSession daoSession;
    private final Context appContext;

    public DownloadManager(OkHttpClient client, Context appContext) {
        this.client = client;
        this.appContext = appContext;
        SQLiteDatabase db = new DevOpenHelper(appContext, DB_NAME).getWritableDatabase();
        daoSession = new DaoMaster(db).newSession();
    }

    public static void init(OkHttpClient client, Context context) {
        if (context == null) {
            throw new NullPointerException("Context cannot be null!");
        }

        if (client == null) {
            throw new NullPointerException("Http client cannot be null!");
        }

        if (instance == null) {
            synchronized (DownloadManager.class) {
                if (instance == null) {
                    instance = new DownloadManager(client, context);
                }
            }
        }
    }

    public static void init(Context context) {
        init(new OkHttpClient(), context);
    }


    public static DownloadManager instance() {
        if (instance == null) {
            throw new IllegalStateException("init download manager by DownloadManager.init(Context context) first!");
        }
        return instance;
    }
}
