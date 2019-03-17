package com.lyc.yuchuan_downloader.utils

import android.os.Environment
import com.lyc.yuchuan_downloader.Preference
import okhttp3.Cache
import okhttp3.OkHttpClient

/**
 * @author liuyuchuan
 * @date 2019/3/16
 * @email kevinliu.sir@qq.com
 */
object Network {
    private var cache = Cache(
        Environment.getDownloadCacheDirectory(),
        Preference.MAX_CACHE_SIZE
    )
    var client = OkHttpClient.Builder().cache(cache).build()
        private set(value) {
            field = value
        }
}
