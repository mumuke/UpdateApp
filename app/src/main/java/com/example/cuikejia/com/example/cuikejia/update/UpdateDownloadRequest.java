package com.example.cuikejia.com.example.cuikejia.update;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;

public class UpdateDownloadRequest implements Runnable {
    private String downloadUrl;
    private String localFilePath;
    private UpdateDownloadListener downloadListener;
    private boolean isDownloading = false;
    private long currentLength;
    private DownloadResponseHandler downloadResponseHandler;


    public UpdateDownloadRequest(String downloadUrl, String localFilePath, UpdateDownloadListener downloadListener) {
        this.downloadUrl = downloadUrl;
        this.localFilePath = localFilePath;
        this.downloadListener = downloadListener;
        this.isDownloading = true;
        downloadResponseHandler = new DownloadResponseHandler();
    }

    /**
     * 真正去建立连接的方法
     *
     * @throws IOException
     * @throws InterruptedException
     */
    private void makeRequest() throws IOException, InterruptedException {
        if (!Thread.currentThread().isInterrupted()) {
            try {
                URL url = new URL(downloadUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Connection", "Keep_Alive");
                connection.connect();//阻塞我们当前的线程

                currentLength = connection.getContentLength();
                if (!Thread.currentThread().isInterrupted()) {
                    //真正的完成文件的下载
                    downloadResponseHandler.sendResponseMessage(connection.getInputStream());
                }
            } catch (IOException e) {
                throw e;
            }
        }
    }

    @Override
    public void run() {
        try {
            makeRequest();
        } catch (IOException e) {

        } catch (InterruptedException e) {

        }
    }

    /**
     * 格式化数字
     *
     * @param value
     * @return
     */
    private String getTwoPointFloatStr(float value) {
        DecimalFormat dformat = new DecimalFormat("0.00");
        return dformat.format(value);
    }

    /**
     * 下载过程中所有的异常
     */
    public enum FailureCode {
        UnknownHost, Socket, SocketTimeout, ConnectTimeout, IO, HttpResponse, JSON, Interrupted
    }

    /**
     * 用来真正的去下载文件，并发送消息和回调消息的接口
     */
    public class DownloadResponseHandler {
        protected static final int SUCCESS_MESSAGE = 0;
        protected static final int FAILURE_MESSAGE = 1;
        protected static final int START_MESSAGE = 2;
        protected static final int FINISH_MESSAGE = 3;
        protected static final int NETWORK_OFF = 4;
        protected static final int PROGRESS_CHANGED = 5;

        private int mCompleteSize = 0;
        private int progress = 0;
        private Handler handler;

        public DownloadResponseHandler() {
            handler = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    handleSelfMessage(msg);
                }
            };
        }

        /**
         * 发送不同的消息
         */
        protected void sendFinishMessage() {
            sendMessage(obtainMessage(FINISH_MESSAGE, null));
        }

        protected void sendProgressChangedMessage(int progress) {
            sendMessage(obtainMessage(PROGRESS_CHANGED, new Object[]{progress}));
        }

        protected void sendFailureMessage(FailureCode failure) {
            sendMessage(obtainMessage(FAILURE_MESSAGE, new Object[]{failure}));
        }

        private void sendMessage(Message msg) {
            if (handler != null) {
                handler.sendMessage(msg);
            } else {
                handleSelfMessage(msg);
            }
        }

        /**
         * 获取一个消息对象
         *
         * @param responseMessage
         * @param response
         * @return
         */
        protected Message obtainMessage(int responseMessage, Object response) {
            Message msg = null;
            if (handler != null) {
                msg = handler.obtainMessage(responseMessage, response);
            } else {
                msg = Message.obtain();
                msg.what = responseMessage;
                msg.obj = response;
            }
            return msg;
        }

        protected void handleSelfMessage(Message msg) {
            Object[] response;
            switch (msg.what) {
                //下载失败
                case FAILURE_MESSAGE:
                    response = (Object[]) msg.obj;
                    handleFailureMessage((FailureCode) response[0]);
                    break;
                //下载改变
                case PROGRESS_CHANGED:
                    response = (Object[]) msg.obj;
                    handleProgressChangedMessage(((Integer) response[0]).intValue());
                    break;
                //下载成功
                case FINISH_MESSAGE:
                    onFinish();
                    break;
            }
        }

        protected void handleProgressChangedMessage(int progress) {
        }

        protected void handleFailureMessage(FailureCode failureCode) {
            onFailure(failureCode);
        }

        /**
         * 外部接口成功的回调
         */
        public void onFinish() {
            downloadListener.onComplete(mCompleteSize, "");
        }

        /**
         * 外部接口失败的回调
         *
         * @param failureCode
         */
        public void onFailure(FailureCode failureCode) {
            downloadListener.onFailure();
        }

        /**
         * 文件 下载方法，会发送各种类型的事件
         */
        void sendResponseMessage(InputStream is) {
            RandomAccessFile randomAccessFile = null;
            mCompleteSize = 0;

            try {
                byte[] buffer = new byte[1024];
                int length = -1;
                int limit = 0;
                randomAccessFile = new RandomAccessFile(localFilePath, "rwd");
                while ((length = is.read(buffer)) != -1) {
                    if (isDownloading) {
                        randomAccessFile.write(buffer, 0, length);
                        mCompleteSize += length;
                        if (mCompleteSize < currentLength) {
                            progress = (int) Float.parseFloat(getTwoPointFloatStr(mCompleteSize / currentLength));
                            if (limit / 30 == 0 || progress <= 100) {
                                //为了限制notification的更新频率
                                sendProgressChangedMessage(progress);
                            }
                            limit++;
                        }
                    }
                }
                sendFinishMessage();
            } catch (IOException e) {
                sendFailureMessage(FailureCode.IO);
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    }
                    if (randomAccessFile != null) {
                        randomAccessFile.close();
                    }
                } catch (IOException e) {
                    sendFailureMessage(FailureCode.IO);
                }
            }
        }
    }
}
