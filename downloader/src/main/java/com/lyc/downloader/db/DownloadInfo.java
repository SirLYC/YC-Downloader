package com.lyc.downloader.db;

import com.lyc.downloader.DownloadTask.DownloadState;
import org.greenrobot.greendao.DaoException;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.NotNull;
import org.greenrobot.greendao.annotation.ToMany;

import java.util.List;

/**
 * Created by Liu Yuchuan on 2019/4/22.
 */
@Entity(
        indexes = {
                @org.greenrobot.greendao.annotation.Index(value = "url DESC")
        }
)
public class DownloadInfo {
    @Id(autoincrement = true)
    private Long id;
    @NotNull
    private String url;
    @NotNull
    private String path;
    @ToMany(referencedJoinProperty = "downloadInfoId")
    private List<CustomerHeader> customerHeaders;
    @DownloadState
    private int downloadItemState;
    @ToMany(referencedJoinProperty = "downloadInfoId")
    private List<DownloadThreadInfo> downloadThreadInfos;
    /**
     * Used to resolve relations
     */
    @Generated(hash = 2040040024)
    private transient DaoSession daoSession;
    /**
     * Used for active entity operations.
     */
    @Generated(hash = 1465593784)
    private transient DownloadInfoDao myDao;

    @Generated(hash = 121570573)
    public DownloadInfo(Long id, @NotNull String url, @NotNull String path,
                        int downloadItemState) {
        this.id = id;
        this.url = url;
        this.path = path;
        this.downloadItemState = downloadItemState;
    }

    @Generated(hash = 327086747)
    public DownloadInfo() {
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPath() {
        return this.path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getDownloadItemState() {
        return this.downloadItemState;
    }

    public void setDownloadItemState(int downloadItemState) {
        this.downloadItemState = downloadItemState;
    }

    /**
     * To-many relationship, resolved on first access (and after reset).
     * Changes to to-many relations are not persisted, make changes to the target entity.
     */
    @Generated(hash = 1083281654)
    public List<CustomerHeader> getCustomerHeaders() {
        if (customerHeaders == null) {
            final DaoSession daoSession = this.daoSession;
            if (daoSession == null) {
                throw new DaoException("Entity is detached from DAO context");
            }
            CustomerHeaderDao targetDao = daoSession.getCustomerHeaderDao();
            List<CustomerHeader> customerHeadersNew = targetDao
                    ._queryDownloadInfo_CustomerHeaders(id);
            synchronized (this) {
                if (customerHeaders == null) {
                    customerHeaders = customerHeadersNew;
                }
            }
        }
        return customerHeaders;
    }

    /**
     * Resets a to-many relationship, making the next get call to query for a fresh result.
     */
    @Generated(hash = 433013855)
    public synchronized void resetCustomerHeaders() {
        customerHeaders = null;
    }

    /**
     * To-many relationship, resolved on first access (and after reset).
     * Changes to to-many relations are not persisted, make changes to the target entity.
     */
    @Generated(hash = 1506040892)
    public List<DownloadThreadInfo> getDownloadThreadInfos() {
        if (downloadThreadInfos == null) {
            final DaoSession daoSession = this.daoSession;
            if (daoSession == null) {
                throw new DaoException("Entity is detached from DAO context");
            }
            DownloadThreadInfoDao targetDao = daoSession.getDownloadThreadInfoDao();
            List<DownloadThreadInfo> downloadThreadInfosNew = targetDao
                    ._queryDownloadInfo_DownloadThreadInfos(id);
            synchronized (this) {
                if (downloadThreadInfos == null) {
                    downloadThreadInfos = downloadThreadInfosNew;
                }
            }
        }
        return downloadThreadInfos;
    }

    /**
     * Resets a to-many relationship, making the next get call to query for a fresh result.
     */
    @Generated(hash = 1600557048)
    public synchronized void resetDownloadThreadInfos() {
        downloadThreadInfos = null;
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#delete(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 128553479)
    public void delete() {
        if (myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.delete(this);
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#refresh(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 1942392019)
    public void refresh() {
        if (myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.refresh(this);
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#update(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 713229351)
    public void update() {
        if (myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.update(this);
    }

    /**
     * called by internal mechanisms, do not call yourself.
     */
    @Generated(hash = 17038220)
    public void __setDaoSession(DaoSession daoSession) {
        this.daoSession = daoSession;
        myDao = daoSession != null ? daoSession.getDownloadInfoDao() : null;
    }
}
