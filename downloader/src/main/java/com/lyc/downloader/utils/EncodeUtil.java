package com.lyc.downloader.utils;

import android.util.LruCache;
import com.lyc.downloader.BuildConfig;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
public class EncodeUtil {

    // 缓存最多128条hash记录
    // hash值最多占内存128 * 16B = 2KB
    private static final LruCache<String, String> md5Cache = new LruCache<>(128);

    public static String toMd5(String str) {
        String md5 = md5Cache.get(str);
        if (md5 != null) {
            return md5;
        }

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] hash = messageDigest.digest(str.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                if ((b & 0xFF) < 0x10) {
                    hex.append("0");
                }
                hex.append(Integer.toHexString(b & 0xFF));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // should not happen
            if (BuildConfig.DEBUG) {
                e.printStackTrace();
            }
        }

        return null;
    }
}
