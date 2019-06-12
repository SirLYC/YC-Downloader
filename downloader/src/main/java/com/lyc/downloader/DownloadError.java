package com.lyc.downloader;

/**
 * @author liuyuchuan
 * @date 2019/4/10
 * @email kevinliu.sir@qq.com
 */
public class DownloadError {
    /**
     * nonfatal errors: [0, 100)
     * when these error happens, the downloader behaves like pause
     */
    public static final int ERROR_FILE_EXITS = 0;
    public static final int ERROR_MOBILE_NET = 1;
    public static final int ERROR_DOWNLOAD_FAIL = 2;
    public static final int ERROR_WRITE_FILE = 3;
    public static final int ERROR_NETWORK = 4;
    public static final int ERROR_CONNECT = 5;
    /**
     * fatal errors: >= 100
     * cannot be resumed
     */
    public static final int ERROR_SPACE_FULL = 100;
    public static final int ERROR_CREATE_DIR = 101;
    public static final int ERROR_CREATE_TASK = 102;
    public static final int ERROR_ILLEGAL_URL = 103;
    public static final int ERROR_EMPTY_RESPONSE = 104;
    public static final int ERROR_CONNECT_FATAL = 105;
    public static final int ERROR_CONTENT_EXPIRED = 106;
    private static final DownloadError instance = new DownloadError();
    private Translator translator = new DefaultTranslator();

    private DownloadError() {
    }

    static DownloadError instance() {
        return instance;
    }

    boolean isFatal(int code) {
        return code >= 100;
    }

    void setTranslator(Translator translator) {
        if (translator == null) {
            throw new NullPointerException("DownloadError#translator cannot be null");
        }
        this.translator = translator;
    }

    String translate(int code) {
        return translator.translate(code);
    }

    /**
     * translate code to text
     */
    public interface Translator {
        String translate(int code);
    }

    private static class DefaultTranslator implements Translator {

        @Override
        public String translate(int code) {
            switch (code) {
                case ERROR_FILE_EXITS:
                    return "文件已存在";
                case ERROR_MOBILE_NET:
                    return "运营商网络下载";
                case ERROR_DOWNLOAD_FAIL:
                    return "下载失败";
                case ERROR_WRITE_FILE:
                    return "写入文件失败";
                case ERROR_NETWORK:
                    return "网络错误";
                case ERROR_SPACE_FULL:
                    return "空间不足";
                case ERROR_CREATE_DIR:
                    return "创建文件夹失败";
                case ERROR_CREATE_TASK:
                    return "创建任务失败";
                case ERROR_ILLEGAL_URL:
                    return "url不合法";
                case ERROR_EMPTY_RESPONSE:
                    return "服务器无可用资源";
                case ERROR_CONNECT:
                case ERROR_CONNECT_FATAL:
                    return "连接失败";
                case ERROR_CONTENT_EXPIRED:
                    return "资源过期，请重试";
                default:
                    return "未知错误";
            }
        }
    }
}
