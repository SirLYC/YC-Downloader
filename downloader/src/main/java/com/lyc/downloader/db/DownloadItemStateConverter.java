package com.lyc.downloader.db;

import org.greenrobot.greendao.converter.PropertyConverter;

/**
 * Created by Liu Yuchuan on 2019/4/22.
 */
public class DownloadItemStateConverter implements PropertyConverter<DownloadItemState, Integer> {
    @Override
    public DownloadItemState convertToEntityProperty(Integer databaseValue) {
        return DownloadItemState.values()[databaseValue];
    }

    @Override
    public Integer convertToDatabaseValue(DownloadItemState entityProperty) {
        return entityProperty.ordinal();
    }
}
