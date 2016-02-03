package com.example.vigi.androiddownload.download;

import java.io.InputStream;

public abstract class NetWorkResponse {
    public long totalLength;
    public long contentLength;
    public InputStream contentStream;
    public boolean supportRange = false;

    public abstract void disconnect();

}