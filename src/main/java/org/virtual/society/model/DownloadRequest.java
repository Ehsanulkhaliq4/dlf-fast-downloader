package org.virtual.society.model;

public class DownloadRequest {
    private String url;
    private String formatId;

    // Constructors
    public DownloadRequest() {}

    public DownloadRequest(String url, String formatId) {
        this.url = url;
        this.formatId = formatId;
    }

    // Getters and setters
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getFormatId() { return formatId; }
    public void setFormatId(String formatId) { this.formatId = formatId; }
}
