package com.lyc.downloader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.annotation.Nullable;
import com.lyc.downloader.utils.StringUtil;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import java.io.File;

/**
 * @author liuyuchuan
 * @date 2019/4/8
 * @email kevinliu.sir@qq.com
 */
public class MainActivity extends Activity implements View.OnClickListener {

    private EditText urlEdit;
    private Button startButton;
    private Button pauseButton;
    private Button cancelButton;
    private Button deleteButton;
    private Button openButton;
    private TextView stateTextView;
    private ProgressBar progressBar;
    private DownloadTask downloadTask;
    private DownloadListener downloadListener = new DownloadListener() {
        @Override
        public void onPrepared() {
            runOnUiThread(() -> stateTextView.setText(("ready")));
        }

        @Override
        public void onProgressUpdate(long cur, long total) {
            if (total == -1) {
                progressBar.setProgress(0);
            } else {
                runOnUiThread(() -> progressBar.setProgress((int) (cur * 1000.0 / total)));
            }
        }

        @Override
        public void onSpeedChange(double bps) {
            runOnUiThread(() -> {
                if (downloadTask.getState() == DownloadTask.RUNNING) {
                    stateTextView.setText(StringUtil.bpsToString(bps));
                }
            });
        }

        @Override
        public void onDownloadError(String reason, boolean fatal) {
            runOnUiThread(() -> stateTextView.setText(("error : " + reason + ", fatal : " + fatal)));
        }

        @Override
        public void onDownloadStart() {
            runOnUiThread(() -> stateTextView.setText(("starting")));
        }

        @Override
        public void onDownloadPausing() {
            runOnUiThread(() -> stateTextView.setText(("pausing")));
        }

        @Override
        public void onDownloadPaused() {
            runOnUiThread(() -> stateTextView.setText(("paused")));
        }

        @Override
        public void onDownloadCancelling() {
            runOnUiThread(() -> stateTextView.setText(("cancelling")));
        }

        @Override
        public void onDownloadCanceled() {
            runOnUiThread(() -> {
                progressBar.setProgress(0);
                stateTextView.setText(("canceled"));
            });
        }

        @Override
        public void onDownloadFinished() {
            runOnUiThread(() -> stateTextView.setText(("finished")));
        }
    };
    private String path;
    private OkHttpClient okHttpClient = new OkHttpClient();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        assignViews();

        path = getObbDir().getAbsolutePath() + File.separator + "yuchuan-download";
    }

    private void assignViews() {
        urlEdit = findViewById(R.id.url);
        startButton = findViewById(R.id.start);
        pauseButton = findViewById(R.id.pause);
        cancelButton = findViewById(R.id.cancel);
        deleteButton = findViewById(R.id.delete);
        openButton = findViewById(R.id.open);
        stateTextView = findViewById(R.id.download_state);
        progressBar = findViewById(R.id.progress);
        progressBar.setMax(1000);
        startButton.setOnClickListener(this);
        pauseButton.setOnClickListener(this);
        cancelButton.setOnClickListener(this);
        deleteButton.setOnClickListener(this);
        openButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start: {
                String url = urlEdit.getText().toString();
                if (HttpUrl.parse(url) == null) {
                    Toast.makeText(this, "url illegal!", Toast.LENGTH_SHORT).show();
                }
                if (downloadTask == null) {
                    downloadTask = new DownloadTask(
                            url,
                            path + File.separator + parseFilename(url),
                            null,
                            okHttpClient,
                            downloadListener);
                    downloadTask.start(false);
                } else {
                    int s = downloadTask.getState();
                    if (s != DownloadTask.RUNNING && s != DownloadTask.PREPARING) {
                        downloadTask.start(false);
                    }
                }
                break;
            }

            case R.id.pause: {
                if (downloadTask != null) {
                    downloadTask.pause();
                }
                break;
            }

            case R.id.cancel: {
                if (downloadTask != null) {
                    downloadTask.cancel();
                }
                break;
            }

            case R.id.delete: {
                if (downloadTask != null) {
                    new File(downloadTask.path).delete();
                } else {
                    new File(path + File.separator + parseFilename(urlEdit.getText().toString())).delete();
                }
                break;
            }

            case R.id.open: {
                if (downloadTask != null) {
                    Uri uri = Uri.fromFile(new File(downloadTask.path));
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    Log.d("MainActivity", "install " + downloadTask.path);
                    startActivity(intent);
                }
                break;
            }
        }
    }

    private String parseFilename(String url) {
        int index = url.lastIndexOf("/");
        return url.substring(index + 1);
    }
}
