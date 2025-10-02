package org.virtual.society.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.virtual.society.model.DownloadStatus;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DownloadProgressService {
    private final Map<String, DownloadStatus> progressMap = new ConcurrentHashMap<>();

    public void updateProgress(String downloadId, String status, int progress, String message) {
        progressMap.put(downloadId, new DownloadStatus(status, progress, message, new Date()));
    }
    public DownloadStatus getProgress(String downloadId) {
        return progressMap.getOrDefault(downloadId, new DownloadStatus("unknown", 0, "Not found", new Date()));
    }

    public void removeProgress(String downloadId) {
        progressMap.remove(downloadId);
    }
}

