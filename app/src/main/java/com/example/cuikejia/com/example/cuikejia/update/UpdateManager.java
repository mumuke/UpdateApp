package com.example.cuikejia.com.example.cuikejia.update;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

public class UpdateManager {
    private static UpdateManager updateManager;
    private ThreadPoolExecutor threadPoolExecutor;
    private UpdateDownloadRequest updateDownloadRequest;

    private UpdateManager() {
        threadPoolExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        updateManager = new UpdateManager();
    }

    public static UpdateManager getInstance() {
        if (updateManager == null) {
            updateManager = new UpdateManager();
        }
        return updateManager;
    }

    public void startDownloads(String downloadUrl, String localPath,
                               UpdateDownloadListener listener) {
        if (updateDownloadRequest != null) {
            return;
        }

        checkFilePath(localPath);
        //开始真正的去下载任务
        updateDownloadRequest = new UpdateDownloadRequest(downloadUrl, localPath, listener);
        Future<?> future = threadPoolExecutor.submit(updateDownloadRequest);
    }

    /**
     * 用来检查文件路径是否已经存在
     *
     * @param localPath
     */
    private void checkFilePath(String localPath) {
        File dir = new File(localPath.substring(0, localPath.lastIndexOf("/") + 1));
        if (!dir.exists()) {
            dir.mkdir();
        }

        File file = new File(localPath);
        if (!dir.exists()) {
            try {
                file.createNewFile();
            } catch (Exception e) {
            }
        }
    }


}
