package org.virtual.society.dto;

public class DownloadResponse {
    public String jobId;
    public String filename;
    public String status; // QUEUED/RUNNING/DONE/ERROR
    public String message;
}
