package com.lyc.downloader;

import okhttp3.*;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    Call call = null;

    @Test
    public void addition_isCorrect() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            OkHttpClient client = new OkHttpClient();
            call = client.newCall(new Request.Builder().url("https://www.google.com").build());
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, Response response) {
                    System.out.println(response);
                }
            });
            countDownLatch.countDown();
        });
        t.start();
        countDownLatch.await();
        call.cancel();
        Thread.sleep(1000);
    }
}
