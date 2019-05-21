package com.lyc.yuchuan_downloader

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.layout_delete_item.view.*

/**
 * Created by Liu Yuchuan on 2019/5/20.
 */
class DeleteDownloadDialog : DialogFragment() {
    private lateinit var checkBox: CheckBox
    private lateinit var mainViewModel: MainViewModel

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = context!!
        val view = LayoutInflater.from(ctx).inflate(R.layout.layout_delete_item, null)
        val id = arguments?.getLong("id")
        mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)
        checkBox = view.cb_delete_file
        return AlertDialog.Builder(ctx)
            .setView(view)
            .setPositiveButton(getString(R.string.yes)) { d, w ->
                id?.let {
                    mainViewModel.delete(it, checkBox.isChecked)
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .create()
    }
}
