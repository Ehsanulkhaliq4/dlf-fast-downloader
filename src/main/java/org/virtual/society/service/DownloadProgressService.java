package org.virtual.society.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.virtual.society.model.DownloadProgress;
import org.virtual.society.model.DownloadStatus;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DownloadProgressService {
    private final Map<String, DownloadProgress> progressMap = new ConcurrentHashMap<>();

    public void updateProgress(String downloadId, double percentage, String status, String speed, String eta) {
        DownloadProgress progress = new DownloadProgress(downloadId, percentage, status, speed, eta, System.currentTimeMillis());
        progressMap.put(downloadId, progress);
    }

    public DownloadProgress getProgress(String downloadId) {
        return progressMap.get(downloadId);
    }

    public void removeProgress(String downloadId) {
        progressMap.remove(downloadId);
    }
}

