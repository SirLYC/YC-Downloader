package com.lyc.yuchuan_downloader

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProviders

/**
 * Created by Liu Yuchuan on 2019/5/20.
 */
class RestartDownloadDialog : DialogFragment() {
    private lateinit var mainViewModel: MainViewModel

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = context!!
        val id = arguments?.getLong("id")
        mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)
        return AlertDialog.Builder(ctx)
            .setMessage("重新下载会删除已下载项，是否继续？")
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                id?.let {
                    mainViewModel.restart(it)
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .create()
    }
}
