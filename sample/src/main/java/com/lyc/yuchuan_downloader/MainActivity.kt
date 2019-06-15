package com.lyc.yuchuan_downloader

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.lyc.downloader.DownloadTask.*
import com.lyc.downloader.YCDownloader
import com.lyc.yuchuan_downloader.util.ReactiveAdapter
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File

/**
 * Created by Liu Yuchuan on 2019/4/22.
 */
class MainActivity : AppCompatActivity(), TextWatcher, DownloadItemViewBinder.OnItemButtonClickListener {
    private var index: Int = 0
    private lateinit var mainViewModel: MainViewModel
    private val testLinks = arrayOf(
        "https://download.alicdn.com/dingtalk-desktop/win_installer/Release/DingTalk_v4.6.21.50011.exe?key=4978092c5535384bfbb2ead8fed57be6&tmp=1556028679291",
        "https://cdn.mysql.com//Downloads/MySQL-5.7/mysql-5.7.25-macos10.14-x86_64.dmg",
        "http://www.officeplus.cn/t/9/92A75810F24C716EB58A695A8A14A0FF.pptx",
        "http://www.zgtsshzy.net/CN/article/downloadArticleFile.do?attachType=PDF&id=567"
    )
    private lateinit var adapter: ReactiveAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mainViewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        et.setText(testLinks[index])
        mainViewModel.setup(File(obbDir, "downloader").absolutePath)
        new_task.setOnClickListener {
            val text = et.text.toString().trim { it <= ' ' }
            if (testLinks[index] == text) {
                index = (index + 1) % testLinks.size
            }
            et.setText(testLinks[index])
            mainViewModel.submit(text)
        }
        delete!!.setOnClickListener {
            val file = File(mainViewModel.path)
            if (file.exists() && file.isDirectory) {
                val files = file.listFiles()
                if (files != null) {
                    for (f in files) {
                        if (!f.delete()) {
                            f.deleteOnExit()
                        }
                    }
                }
            }
        }
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ReactiveAdapter(mainViewModel.itemList).apply {
            register(String::class, GroupHeaderItemViewBinder())
            register(DownloadItem::class, DownloadItemViewBinder(this@MainActivity))
            observe(this@MainActivity)
            rv.adapter = this
        }
        val itemAnimator = rv.itemAnimator
        if (itemAnimator != null) {
            itemAnimator.changeDuration = 0
            val animator = itemAnimator as SimpleItemAnimator?
            animator!!.supportsChangeAnimations = false
        }

        mainViewModel.failLiveData.observe(this, Observer {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
        })

        val list = (0..YCDownloader.getMaxSupportRunningTask()).toList()

        spinner.adapter = ArrayAdapter<Int>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            list
        )

        YCDownloader.postOnConnection {
            val index = list.indexOf(YCDownloader.getMaxRunningTask())
            if (index != -1) {
                spinner.setSelection(index)
            }
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {

            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                YCDownloader.setMaxRunningTask(list[position])
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.add) {
            true
        } else super.onOptionsItemSelected(item)

    }


    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {

    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        new_task!!.isEnabled = s.isNotEmpty()
    }

    override fun afterTextChanged(s: Editable) {

    }

    override fun pauseItem(item: DownloadItem) {
        mainViewModel.pause(item.id)
    }

    override fun startItem(item: DownloadItem) {
        mainViewModel.start(item.id)
    }

    override fun onItemLongClicked(item: DownloadItem, view: View): Boolean {
        val state = item.downloadState
        val popupMenu = PopupMenu(this, view)
        val menu = popupMenu.menu
        menu.add(0, 1, 0, "删除")
        when (state) {
            PAUSED, FINISH -> menu.add(0, 2, 0, "重新下载")
        }
        if (state != FINISH && state != CANCELED) {
            menu.add(0, 3, 0, "取消")
        }
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> showDeleteAssureDialog(item.id)
                2 -> showReDownloadDialog(item.id)
                3 -> mainViewModel.cancel(item.id)
            }
            true
        }
        popupMenu.show()
        return true
    }

    private fun showDeleteAssureDialog(id: Long) {
        val dialog = DeleteDownloadDialog()
        dialog.arguments = Bundle().apply {
            putLong("id", id)
        }
        dialog.show(supportFragmentManager, "delete")
    }

    private fun showReDownloadDialog(id: Long) {
        val dialog = RestartDownloadDialog()
        dialog.arguments = Bundle().apply {
            putLong("id", id)
        }
        dialog.show(supportFragmentManager, "restart")
    }
}
