package com.lyc.yuchuan_downloader;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ItemAnimator;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.lyc.downloader.DownloadManager;

import java.io.File;

/**
 * Created by Liu Yuchuan on 2019/4/22.
 */
public class MainActivity extends AppCompatActivity implements TextWatcher, DownloadItemAdapter.OnItemButtonClickListener {
    int index;
    private MainViewModel mainViewModel;
    private Button add;
    private Button delete;
    private EditText editText;
    private RecyclerView recyclerView;
    private String[] testLinks = new String[]{
            "https://download.alicdn.com/dingtalk-desktop/win_installer/Release/DingTalk_v4.6.21.50011.exe?key=4978092c5535384bfbb2ead8fed57be6&tmp=1556028679291",
            "https://cdn.mysql.com//Downloads/MySQL-5.7/mysql-5.7.25-macos10.14-x86_64.dmg",
            "http://www.officeplus.cn/t/9/92A75810F24C716EB58A695A8A14A0FF.pptx",
            "http://www.zgtsshzy.net/CN/article/downloadArticleFile.do?attachType=PDF&id=567"
    };
    private DownloadItemAdapter downloadItemAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainViewModel = ViewModelProviders.of(this).get(MainViewModel.class);
        assignView();
        editText.setText(testLinks[index]);
        DownloadManager.init(this);
        mainViewModel.setup(new File(getObbDir(), "downloader").getAbsolutePath());
        add.setOnClickListener(v -> {
            String text = editText.getText().toString().trim();
            if (testLinks[index].equals(text)) {
                index = (index + 1) % testLinks.length;
            }
            editText.setText(testLinks[index]);
            mainViewModel.submit(text);
        });
        delete.setOnClickListener(v -> {
            File file = new File(mainViewModel.getPath());
            if (file.exists() && file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (!f.delete()) {
                            f.deleteOnExit();
                        }
                    }
                }
            }
        });
        downloadItemAdapter = new DownloadItemAdapter(mainViewModel.itemList, this);
        mainViewModel.itemList.addCallback(downloadItemAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(downloadItemAdapter);
        ItemAnimator itemAnimator = recyclerView.getItemAnimator();
        if (itemAnimator != null) {
            itemAnimator.setChangeDuration(0);
            SimpleItemAnimator animator = (SimpleItemAnimator) itemAnimator;
            animator.setSupportsChangeAnimations(false);
        }
        mainViewModel.failLiveData.observe(this, s -> {
            if (s != null) {
                Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        mainViewModel.itemList.removeCallback(downloadItemAdapter);
        super.onDestroy();
    }

    private void assignView() {
        add = findViewById(R.id.new_task);
        delete = findViewById(R.id.delete);
        editText = findViewById(R.id.et);
        recyclerView = findViewById(R.id.rv);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.add) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (s.length() == 0) {
            add.setEnabled(false);
        } else {
            add.setEnabled(true);
        }
    }

    @Override
    public void afterTextChanged(Editable s) {

    }

    @Override
    public void pauseItem(DownloadItem item) {
        mainViewModel.pause(item.getId());
    }

    @Override
    public void startItem(DownloadItem item) {
        mainViewModel.start(item.getId());
    }

    @Override
    public void deleteItem(DownloadItem item) {
        mainViewModel.cancel(item.getId());
    }
}
