package com.lyc.downloader;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import com.lyc.downloader.db.*;
import com.lyc.downloader.db.DaoMaster.DevOpenHelper;
import org.greenrobot.greendao.AbstractDao;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * @author liuyuchuan
 * @date 2019/4/22
 * @email kevinliu.sir@qq.com
 */
@RunWith(AndroidJUnit4.class)
public class DBTest {
    private static final String TEST_SUFFIX = "_test";
    private DaoMaster daoMaster;
    private DaoSession daoSession;

    @Before
    public void init() {
        Context context = ApplicationProvider.getApplicationContext();
        SQLiteDatabase db = new DevOpenHelper(context, DownloadManager.DB_NAME + TEST_SUFFIX + ".db").getWritableDatabase();
        daoMaster = new DaoMaster(db);
        daoSession = daoMaster.newSession();
        clear();
    }

    @After
    public void clear() {
        daoSession.runInTx(() -> {
            for (AbstractDao<?, ?> allDao : daoSession.getAllDaos()) {
                allDao.deleteAll();
            }
        });
    }

    @Test
    public void testDB() {
        DownloadInfoDao downloadInfoDao = daoSession.getDownloadInfoDao();

        DownloadInfo downloadInfo = new DownloadInfo(null, "http", "file", DownloadItemState.DOWNLOADING);
        List<CustomerHeader> customerHeaders = new ArrayList<>();
        customerHeaders.add(new CustomerHeader(null, 0, "A", "A"));
        customerHeaders.add(new CustomerHeader(null, 0, "B", "A"));
        customerHeaders.add(new CustomerHeader(null, 0, "C", "A"));
        customerHeaders.add(new CustomerHeader(null, 0, "D", "A"));
        long id = downloadInfoDao.insert(downloadInfo);
        Assert.assertEquals(new Long(id), downloadInfo.getId());
        Assert.assertEquals(1, downloadInfoDao.loadAll().size());
        Assert.assertEquals(0, downloadInfo.getCustomerHeaders().size());
        Assert.assertEquals(0, downloadInfo.getDownloadThreadInfos().size());

        for (CustomerHeader customerHeader : customerHeaders) {
            customerHeader.setDownloadInfoId(id);
        }

        CustomerHeaderDao customerHeaderDao = daoSession.getCustomerHeaderDao();
        customerHeaderDao.saveInTx(customerHeaders);
        Assert.assertEquals(customerHeaders.size(), customerHeaderDao.loadAll().size());

        for (CustomerHeader customerHeader : customerHeaders) {
            Assert.assertNotEquals(null, customerHeader.getDownloadInfoId());
        }

        for (int i = 1; i < customerHeaders.size(); i++) {
            Assert.assertNotEquals(customerHeaders.get(i - 1).getId(), customerHeaders.get(i).getId());
        }

        Assert.assertEquals(0, downloadInfo.getCustomerHeaders().size());
        downloadInfo.resetCustomerHeaders();
        Assert.assertEquals(downloadInfo.getCustomerHeaders().size(), customerHeaders.size());

        List<DownloadThreadInfo> downloadThreadInfoList = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            downloadThreadInfoList.add(new DownloadThreadInfo(null, i, 0, 0, 0, id));
        }
        DownloadThreadInfoDao downloadThreadInfoDao = daoSession.getDownloadThreadInfoDao();
        downloadThreadInfoDao.saveInTx(downloadThreadInfoList);
        Assert.assertEquals(downloadThreadInfoList.size(), customerHeaderDao.loadAll().size());

        for (int i = 1; i < downloadThreadInfoList.size(); i++) {
            Assert.assertNotEquals(downloadThreadInfoList.get(i - 1), downloadThreadInfoList.get(i));
        }

        Assert.assertEquals(0, downloadInfo.getDownloadThreadInfos().size());
        downloadInfo.resetDownloadThreadInfos();
        Assert.assertEquals(downloadThreadInfoList.size(), downloadInfo.getDownloadThreadInfos().size());

        daoSession.runInTx(() -> {
            for (CustomerHeader customerHeader : downloadInfo.getCustomerHeaders()) {
                customerHeaderDao.delete(customerHeader);
            }

            downloadThreadInfoDao.deleteInTx(downloadThreadInfoList);
            for (DownloadThreadInfo downloadThreadInfo : downloadInfo.getDownloadThreadInfos()) {
                downloadThreadInfoDao.delete(downloadThreadInfo);
            }

            downloadInfo.delete();
        });

        Assert.assertEquals(downloadInfoDao.loadAll().size(), 0);
        Assert.assertEquals(downloadThreadInfoDao.loadAll().size(), 0);
        Assert.assertEquals(customerHeaderDao.loadAll().size(), 0);
    }
}
