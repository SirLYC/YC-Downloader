package com.lyc.downloader;

import android.util.SparseArray;
import com.lyc.downloader.db.DaoSession;
import com.lyc.downloader.db.DownloadInfo;
import com.lyc.downloader.db.DownloadInfoDao;
import com.lyc.downloader.db.DownloadThreadInfo;
import com.lyc.downloader.db.DownloadThreadInfoDao;
import com.lyc.downloader.utils.Logger;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Created by Liu Yuchuan on 2019/5/21.
 */
class PersistUtil {

    private static final Comparator<DownloadThreadInfo> THREAD_INFO_COMPARATOR = (o1, o2) -> {
        if (o1 == null) {
            return o2 == null ? 0 : -1;
        }

        if (o2 == null) {
            return 1;
        }

        Long id1 = o1.getId();
        Long id2 = o2.getId();
        if (id1 == null) {
            return id2 == null ? 0 : -1;
        }

        if (id2 == null) {
            return 1;
        }

        return Long.compare(id1, id2);
    };

    static void persisDownloadThreadInfoQuietly(DaoSession daoSession, DownloadThreadInfo downloadThreadInfo) {
        DownloadThreadInfoDao downloadThreadInfoDao = daoSession.getDownloadThreadInfoDao();
        try {
            downloadThreadInfoDao.save(downloadThreadInfo);
        } catch (Exception e) {
            Logger.e("PersistUtil", "persisDownloadThreadInfoQuietly", e);
        }
    }

    static void persistDownloadInfoQuietly(DaoSession daoSession, DownloadInfo downloadInfo,
                                           SparseArray<DownloadThreadInfo> downloadThreadInfos) {
        try {
            persistDownloadInfo(daoSession, downloadInfo, downloadThreadInfos);
        } catch (Exception e) {
            Logger.e("PersistUtil", "cannot persist downloadInfo", e);
        }
    }

    static Long persistDownloadInfo(DaoSession daoSession, DownloadInfo downloadInfo,
                                    SparseArray<DownloadThreadInfo> downloadThreadInfos) throws Exception {
        if (downloadInfo == null) {
            return null;
        }
        DownloadInfoDao downloadInfoDao = daoSession.getDownloadInfoDao();
        DownloadThreadInfoDao downloadThreadInfoDao = daoSession.getDownloadThreadInfoDao();
        return daoSession.callInTx(() -> {
            downloadInfoDao.save(downloadInfo);
            Long infoId = downloadInfo.getId();
            if (infoId == null) {
                return null;
            }

            if (downloadThreadInfos != null) {
                downloadInfo.resetDownloadThreadInfos();
                List<DownloadThreadInfo> oldDownloadThreadInfos = downloadInfo.getDownloadThreadInfos();
                boolean needDelete = oldDownloadThreadInfos.size() != downloadThreadInfos.size();
                if (!needDelete) {
                    Collections.sort(oldDownloadThreadInfos, THREAD_INFO_COMPARATOR);

                    for (int i = 0, s = oldDownloadThreadInfos.size(); i < s; i++) {
                        if (!Objects.equals(downloadThreadInfos.get(i).getId(), oldDownloadThreadInfos.get(i).getId())) {
                            needDelete = true;
                            break;
                        }
                    }
                }
                if (needDelete) {
                    for (DownloadThreadInfo oldDownloadThreadInfo : oldDownloadThreadInfos) {
                        downloadThreadInfoDao.delete(oldDownloadThreadInfo);
                    }
                }
                for (int i = 0, s = downloadThreadInfos.size(); i < s; i++) {
                    DownloadThreadInfo downloadThreadInfo = downloadThreadInfos.valueAt(i);
                    downloadThreadInfo.setDownloadInfoId(infoId);
                    downloadThreadInfoDao.save(downloadThreadInfo);
                }
            }
            return infoId;
        });

    }

    static void deleteDownloadInfo(DaoSession daoSession, DownloadInfo downloadInfo) {
        if (downloadInfo == null || downloadInfo.getId() == null) {
            return;
        }

        downloadInfo.resetDownloadThreadInfos();
        DownloadInfoDao downloadInfoDao = daoSession.getDownloadInfoDao();
        DownloadThreadInfoDao downloadThreadInfoDao = daoSession.getDownloadThreadInfoDao();
        try {
            daoSession.callInTx(() -> {
                downloadInfoDao.delete(downloadInfo);
                downloadThreadInfoDao.queryBuilder()
                        .where(DownloadThreadInfoDao.Properties.DownloadInfoId.eq(downloadInfo.getId()))
                        .buildDelete()
                        .executeDeleteWithoutDetachingEntities();
                return null;
            });
        } catch (Exception e) {
            Logger.e("PersistUtil", "cannot delete downloadInfo", e);
        }
    }

    static void deleteFile(DownloadInfo downloadInfo, boolean deleteDownloadedFile) {
        String filename = downloadInfo.getFilename();
        String path = downloadInfo.getPath();
        if (filename == null || path == null) {
            return;
        }
        if (deleteDownloadedFile) {
            File file = new File(path, filename);
            if (file.exists() && !file.delete()) {
                Logger.e("PersistUtil", "cannot delete file " + file.getAbsolutePath());
            }
        }
        File file = new File(path, filename + Constants.TMP_FILE_SUFFIX);
        if (file.exists() && !file.delete()) {
            Logger.e("PersistUtil", "cannot delete temp file " + file.getAbsolutePath());
        }
    }
}
