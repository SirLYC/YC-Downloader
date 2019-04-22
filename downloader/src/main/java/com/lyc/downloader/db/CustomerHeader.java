package com.lyc.downloader.db;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.Generated;

/**
 * Created by Liu Yuchuan on 2019/4/22.
 */
@Entity
public class CustomerHeader {
    @Id(autoincrement = true)
    private long id;
    @Property(nameInDb = "download_info_id")
    private long downloadInfoId;
    private String key;
    private String value;
    @Generated(hash = 313076657)
    public CustomerHeader(long id, long downloadInfoId, String key, String value) {
        this.id = id;
        this.downloadInfoId = downloadInfoId;
        this.key = key;
        this.value = value;
    }
    @Generated(hash = 1511092188)
    public CustomerHeader() {
    }
    public long getId() {
        return this.id;
    }
    public void setId(long id) {
        this.id = id;
    }
    public long getDownloadInfoId() {
        return this.downloadInfoId;
    }
    public void setDownloadInfoId(long downloadInfoId) {
        this.downloadInfoId = downloadInfoId;
    }
    public String getKey() {
        return this.key;
    }
    public void setKey(String key) {
        this.key = key;
    }
    public String getValue() {
        return this.value;
    }
    public void setValue(String value) {
        this.value = value;
    }
}
