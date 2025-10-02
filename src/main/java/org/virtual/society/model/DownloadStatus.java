package org.virtual.society.model;

import java.util.Date;

public class DownloadStatus {
        private String status;
        private int progress;
        private String message;
        private Date lastUpdate;
        // constructor, getters, setters


    public DownloadStatus(String status, int progress, String message, Date lastUpdate) {
        this.status = status;
        this.progress = progress;
        this.message = message;
        this.lastUpdate = lastUpdate;
    }

    public DownloadStatus() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
}
