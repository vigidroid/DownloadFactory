package com.example.vigi.androiddownload.download;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.SocketException;

/**
 * Created by Vigi on 2016/1/20.
 */
public class DownloadWorker {
    private static final int SLEEP_INTERNAL_MS = 1000;
    private static final int STREAM_BUFFER = 1024;
    private NetWorkPerformer mNetWorkPerformer;
    private DownloadDelivery mDelivery;
    private DownloadRequest mDownloadRequest;

    public DownloadWorker(NetWorkPerformer netWorkPerformer, DownloadDelivery delivery, DownloadRequest request) {
        mNetWorkPerformer = netWorkPerformer;
        mDelivery = delivery;
        mDownloadRequest = request;
    }

    public DownloadResult work() throws InterruptedException {
        while (true) {
            InputStream bis = null;
            CustomOutputStream bos = null;
            NetWorkResponse response = null;
            DownloadException error = null;
            try {
                response = mNetWorkPerformer.performDownloadRequest(mDownloadRequest);
                if (mDownloadRequest.isCancel()) {
                    return null;
                }
                validateServerData(mDownloadRequest, response);
                mDelivery.postTotalLength(mDownloadRequest, response.mTotalLength);

                File targetFile = mDownloadRequest.getTargetFile();
                bis = new BufferedInputStream(response.mContentStream);
                // TODO: 2016/2/1 file io exception
                bos = new CustomOutputStream(generateWriteStream(targetFile, response.mTotalLength, mDownloadRequest.getStartPos()));
                byte[] tmp = new byte[STREAM_BUFFER];
                long downloadedBytes = 0;
                int len;
                // TODO: 2016/2/1 IOException
                // TODO: 2016/2/1 may throw lots kind of exception when bad or no network
                while ((len = bis.read(tmp)) != -1) {
                    bos.customWrite(tmp, 0, len);
                    downloadedBytes += len;
                    mDelivery.postLoading(mDownloadRequest, downloadedBytes);
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    if (mDownloadRequest.isCancel()) {
                        return null;
                    }
                }
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                if (e instanceof IOException) {
                    if (e instanceof MalformedURLException) {
                        error = new DownloadException(DownloadException.BAD_URL, e);
                    } else if (e instanceof SocketException) {
                        error = new DownloadException(DownloadException.NO_CONNECTION, e);
                    } else {
                        error = new DownloadException(DownloadException.UNKNOWN, e);
                    }
                } else if (e instanceof DownloadException) {
                    error = (DownloadException) e;
                } else {
                    // unhandled exception
                    error = new DownloadException(DownloadException.UNKNOWN, e);
                }

                if (error.getExceptionCode() == DownloadException.UNKNOWN_HOST
                        || (error.getExceptionCode() == DownloadException.NO_CONNECTION)) {
                    // TODO: 2016/2/2 timeout handle
                    mDownloadRequest.setStartPos(mDownloadRequest.getCurrentBytes());
                    mDownloadRequest.setDownloadedBytes(0);
                    Thread.sleep(SLEEP_INTERNAL_MS);       // I need have a rest
                    continue;
                }
            } finally {
                if (response != null) {
                    response.disconnect();
                }
                try {
                    if (bis != null) {
                        bis.close();
                    }
                    if (bos != null) {
                        bos.flush();
                        bos.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return new DownloadResult(error);
        }
    }

    private OutputStream generateWriteStream(File file, long fileLength, long startPos) throws DownloadException {
        try {
            if (!file.exists() && startPos == 0) {
                RandomAccessFile raf = new RandomAccessFile(file, "rw");
                raf.setLength(fileLength);
                raf.close();
                return new BufferedOutputStream(new FileOutputStream(file));
            }
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            raf.seek(startPos);
            return new BufferedOutputStream(new FileOutputStream(raf.getFD()));
        } catch (IOException e) {
            throw new DownloadException(DownloadException.LOCAL_IO, e);
        }
    }

    protected void validateServerData(DownloadRequest request, NetWorkResponse response) throws DownloadException {
        // TODO: 2016/1/26 and do not support 0 length download
        if (response.mContentLength == 0 || response.mTotalLength == 0) {
            throw new DownloadException(DownloadException.EXCEPTION_CODE_PARSE, "url(" + request.getOriginalUrl() + ") does not return content length");
        }
    }

    private class CustomOutputStream extends BufferedOutputStream {

        public CustomOutputStream(OutputStream out) {
            super(out);
        }

        public void customWrite(byte[] buffer, int offset, int length) throws DownloadException {
            try {
                super.write(buffer, offset, length);
            } catch (IOException e) {
                throw new DownloadException(DownloadException.LOCAL_IO, e);
            }
        }
    }
}
