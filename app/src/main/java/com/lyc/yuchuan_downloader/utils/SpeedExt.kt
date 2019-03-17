package com.lyc.yuchuan_downloader.utils

import java.text.DecimalFormat
import kotlin.math.roundToInt

/**
 * @author liuyuchuan
 * @date 2019/3/16
 * @email kevinliu.sir@qq.com
 */

private val decimalFormat = DecimalFormat()

fun Float.toSpeed(): String {
    var cur = this
    if (cur < 512) {
        return "${cur.roundTo2()}B/s"
    }

    cur /= 1024
    if (cur < 512) {
        return "${cur.roundTo2()}kB/s"
    }

    cur /= 1024
    if (cur < 512) {
        return "${cur.roundTo2()}MB/s"
    }

    // Jealous _(>_<)_
    cur /= 1024
    if (cur < 512) {
        return "${cur.roundTo2()}GB/s"
    }

    cur /= 1024

    return "${cur.roundTo2()}TB/s"
}


fun Float.roundTo2(): Float {
    return ((this * 100).roundToInt() / 100).toFloat()
}


fun Float.toTime(): String {
    var seconds = this.toInt()

    if (seconds < 60) {
        return "${seconds}s"
    }


    var minutes = seconds / 60
    seconds -= minutes * 60

    if (minutes < 60) {
        return "${minutes}m${
        if (seconds == 0) "" else "${seconds}s"
        }"
    }

    val hours = minutes / 60
    minutes -= hours * 60

    if (hours < 24) {
        return "${hours}h${
        if (minutes == 0) "" else "${minutes}m"
        }"
    }

    return ">=1d"
}
