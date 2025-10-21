package org.virtual.society.model;

public class DownloadProgress {
    private String downloadId;
    private double percentage;
    private String status;
    private String speed;
    private String eta;
    private long lastUpdate;

    public DownloadProgress(String downloadId, double percentage, String status, String speed, String eta, long lastUpdate) {
        this.downloadId = downloadId;
        this.percentage = percentage;
        this.status = status;
        this.speed = speed;
        this.eta = eta;
        this.lastUpdate = lastUpdate;
    }

    public String getDownloadId() {
        return downloadId;
    }

    public void setDownloadId(String downloadId) {
        this.downloadId = downloadId;
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(double percentage) {
        this.percentage = percentage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSpeed() {
        return speed;
    }

    public void setSpeed(String speed) {
        this.speed = speed;
    }

    public String getEta() {
        return eta;
    }

    public void setEta(String eta) {
        this.eta = eta;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
}
