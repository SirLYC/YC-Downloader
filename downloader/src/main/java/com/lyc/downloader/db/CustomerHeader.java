package com.lyc.downloader.db;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;

import java.util.Objects;

/**
 * Created by Liu Yuchuan on 2019/4/22.
 */
@Entity
public class CustomerHeader {
    @Id(autoincrement = true)
    private Long id;
    @Property(nameInDb = "download_info_id")
    private long downloadInfoId;
    private String key;
    private String value;

    @Generated(hash = 1588940521)
    public CustomerHeader(Long id, long downloadInfoId, String key, String value) {
        this.id = id;
        this.downloadInfoId = downloadInfoId;
        this.key = key;
        this.value = value;
    }

    @Generated(hash = 1511092188)
    public CustomerHeader() {
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CustomerHeader that = (CustomerHeader) o;

        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
