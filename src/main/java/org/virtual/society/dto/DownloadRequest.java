package org.virtual.society.dto;

public class DownloadRequest {
    public String url;
    public String format;   // e.g. "best", "bestvideo+bestaudio/best", or audio-only "bestaudio"
    public String filename;
}
