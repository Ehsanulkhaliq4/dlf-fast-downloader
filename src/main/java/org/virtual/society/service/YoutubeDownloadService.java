package org.virtual.society.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.virtual.society.exceptions.DownloadException;
import org.virtual.society.model.DownloadProgress;
import org.virtual.society.model.VideoFormat;
import org.virtual.society.model.VideoInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class YoutubeDownloadService {

private static final String DOWNLOAD_DIR =
        Paths.get(System.getProperty("user.dir"), "downloads").toString() + File.separator;
    private static final Path DOWNLOAD_PATH =
            Paths.get(System.getProperty("user.dir"), "downloads");
    private static final String YT_DLP_COMMAND = "yt-dlp";
    private static final long PROCESS_TIMEOUT = 300;
    private static final int MAX_CACHE_SIZE = 100;
    private final Map<String, VideoInfo> videoInfoCache = new ConcurrentHashMap<>();
    private final ExecutorService downloadExecutor = Executors.newCachedThreadPool();

    @Inject
    DownloadProgressService progressService;

    public VideoInfo getVideoInfo(String videoUrl){
        if (!isValidYouTubeUrl(videoUrl)) {
            throw new DownloadException("Invalid YouTube URL: " + videoUrl);
        }
        // Use cache with size limit
        if (videoInfoCache.size() >= MAX_CACHE_SIZE) {
            videoInfoCache.clear(); // Simple eviction strategy
        }
        // Manual cache implementation (avoids recursion)
        VideoInfo cached = videoInfoCache.get(videoUrl);
        if (cached != null) {
            return cached;
        }

        // Fetch fresh data
        VideoInfo freshInfo = getVideoInfoDirect(videoUrl);
        videoInfoCache.put(videoUrl, freshInfo);
        return freshInfo;
    }

    private VideoInfo getVideoInfoDirect(String videoUrl) {
        try {
            String videoId = extractVideoId(videoUrl);
            if (videoId == null) {
                throw new DownloadException("Invalid YouTube Url - could not extract video ID");
            }
            VideoInfo result = getVideoInfoWithYtDlp(videoUrl);
            return result;
        } catch (Exception e) {
            throw new DownloadException("Failed to fetch video information: " + e.getMessage(), e);
        }
    }

    private boolean isValidYouTubeUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        return url.matches("^(https?://)?(www\\.)?(youtube\\.com|youtu\\.?be)/.+$");
    }

    public CompletableFuture<File> downloadVideo(String videoUrl, String formatId, String downloadId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String videoId = extractVideoId(videoUrl);
                if (videoId == null) {
                    throw new DownloadException("Invalid YouTube URL");
                }
                Path downloadPath = Paths.get(DOWNLOAD_DIR);
                if (!Files.exists(downloadPath)) {
                    Files.createDirectories(downloadPath);
                }
                List<String> command = buildYtDlpCommand(videoUrl, formatId);

                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.directory(DOWNLOAD_PATH.toFile());
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();
                readProcessOutput(process, downloadId);
                boolean finished = process.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new DownloadException("Download timed out after " + PROCESS_TIMEOUT + " seconds");
                }
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    throw new DownloadException("Download failed with exit code: " + exitCode);
                }
                return findDownloadedFile(downloadPath);

            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                progressService.updateProgress(downloadId, 0, "ERROR: " + e.getMessage(), "0 KiB/s", "Unknown");
                throw new DownloadException("Failed to download video", e);
            }
        }, downloadExecutor);
    }

    private List<String> buildYtDlpCommand(String videoUrl, String formatId) {
        List<String> command = new ArrayList<>();
        command.add(YT_DLP_COMMAND);
        command.add("-v");
        // Add format specification
        if (formatId != null && !formatId.isEmpty() && !"best".equals(formatId)) {
            command.add("-f");
            command.add(formatId);
        } else {
            command.add("-f");
            command.add("bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best");
        }

        // Add output template with safe filename
        command.add("-o");
        command.add(DOWNLOAD_DIR + "%(title)s [%(id)s].%(ext)s");

        // Add other options
        command.add("--no-playlist");
        command.add("--merge-output-format");
        command.add("mp4");
        command.add("--no-mtime");
        command.add("--no-overwrites");
        command.add("--continue");
        command.add("--newline");

        // Add the video URL
        command.add(videoUrl);

        return command;
    }
    private void readProcessOutput(Process process, String downloadId) {
        CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Parse progress from yt-dlp output
                    DownloadProgress progress = parseProgressLine(line);
                    if (progress != null) {
                        progressService.updateProgress(
                                downloadId,
                                progress.getPercentage(),
                                progress.getStatus(),
                                progress.getSpeed(),
                                progress.getEta()
                        );
                    }
                }
            } catch (IOException e) {
                System.err.println("Error reading process output for " + downloadId + ": " + e.getMessage());
            }
        });
    }
    private File findDownloadedFile(Path downloadPath) {
        File latestFile = findLatestFile(downloadPath.toFile());
        if (latestFile != null && latestFile.exists() && latestFile.length() > 0) {
            return latestFile;
        } else {
            throw new DownloadException("Could not find downloaded file");
        }
    }
    private File findLatestFile(File directory) {
        File[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            throw new DownloadException("No files found in download directory");
        }

        File latestFile = files[0];
        for (File file : files) {
            if (file.lastModified() > latestFile.lastModified()) {
                latestFile = file;
            }
        }
        return latestFile;
    }
    private String extractVideoId(String url) {
        String pattern = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(url);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
    private VideoInfo getVideoInfoWithYtDlp(String videoUrl){
        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    YT_DLP_COMMAND,
                    "--dump-json",
                    "--no-warnings",
                    videoUrl
            );

            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder jsonOutput = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonOutput.append(line);
            }
            boolean finished = process.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                throw new DownloadException("yt-dlp command timed out after " + PROCESS_TIMEOUT + " seconds");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new DownloadException("yt-dlp failed with exit code: " + exitCode+ ". Output: " + jsonOutput.toString());
            }

            if (jsonOutput.length() == 0) {
                throw new DownloadException("yt-dlp returned empty output");
            }
            return parseYtDlpJsonOutput(jsonOutput.toString());

        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            throw new DownloadException("Failed to execute yt-dlp command: " + e.getMessage(), e);
        }finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
    private VideoInfo parseYtDlpJsonOutput(String jsonOutput){
        try{
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonOutput);
            String id = root.path("id").asText("Unknown");
            String title = root.path("title").asText("Unknown");
            String description = root.path("description").asText("Unknown");
            String thumbnail = root.path("thumbnail").asText("Unknown");
            String duration = root.path("duration").asText();
            String viewCount = root.path("view_count").asText();
            String uploadDate = root.path("upload_date").asText("Unknown");
            List<VideoFormat> formats = parseFormatsFromJson(root);

            return new VideoInfo(id, title, description, thumbnail,
                    formatDuration(duration),
                    formatViews(viewCount),
                    formatUploadDate(uploadDate),
                    formats);
        } catch (Exception e) {
            throw new DownloadException("Failed to parse yt-dlp output", e);
        }
    }
    private List<VideoFormat> parseFormatsFromJson(JsonNode root) {
        List<VideoFormat> formats = new ArrayList<>();
        JsonNode formatsNode = root.path("formats");
        if (formatsNode.isArray()) {
            for (JsonNode format : formatsNode) {
                try {
                    String formatId = format.path("format_id").asText();
                    String extension = format.path("ext").asText();
                    int height = format.path("height").asInt(0);
                    int fps = format.path("fps").asInt(0);
                    String videoCodec = format.path("vcodec").asText("none");
                    String audioCodec = format.path("acodec").asText("none");
                    long fileSize = format.path("filesize").asLong(0);

                    if (!"none".equals(videoCodec)) {
                        // Video format (with or without audio)
                        String quality = height > 0 ? height + "p" : "Unknown";
                        String formatType = "Video";
                        String size = formatFileSize(String.valueOf(fileSize));

                        formats.add(new VideoFormat(formatId, quality, formatType,
                                size, fps, videoCodec, audioCodec));
                    } else if (!"none".equals(audioCodec)) {
                        // Audio-only format
                        String quality = "Audio Only";
                        String formatType = extension.toUpperCase();
                        String size = formatFileSize(String.valueOf(fileSize));
                        int sampleRate = format.path("asr").asInt(0);
                        String bitrate = sampleRate > 0 ? (sampleRate / 1000) + " kHz" : "Unknown";

                        formats.add(new VideoFormat(formatId, quality, formatType,
                                size, 0, null, bitrate));
                    }
                } catch (Exception e) {
                    // Log and skip invalid formats
                    System.err.println("Skipping invalid format: " + e.getMessage());
                }
            }
        }
        return formats;
    }
    private String formatFileSize(String bytes){
        if (bytes == null || "Unknown".equals(bytes)) return "Unknown size";
        try {
            long size = Long.parseLong(bytes);
            if (size < 1024){
                return size + " B";
            }else if (size < 1024 * 1024){
                return String.format("%.1f KB", size / 1024.0);
            }else if (size < 1024 * 1024 * 1024) {
                return String.format("%.1f MB", size / (1024.0 * 1024.0));
            } else {
                return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
            }
        }catch (NumberFormatException e) {
            return "Unknown size";
        }
    }
    private String formatDuration(String seconds) {
        if (seconds == null || "Unknown".equals(seconds)) return "Unknown duration";
        try {
            int totalSeconds = Integer.parseInt(seconds);
            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            int secs = totalSeconds % 60;

            if (hours > 0) {
                return String.format("%d:%02d:%02d", hours, minutes, secs);
            } else {
                return String.format("%d:%02d", minutes, secs);
            }
        } catch (NumberFormatException e) {
            return "Unknown duration";
        }
    }
    private String formatViews(String viewCount) {
        if (viewCount == null || "Unknown".equals(viewCount)) return "Unknown views";
        try {
            long views = Long.parseLong(viewCount);
            if (views >= 1_000_000_000) {
                return String.format("%.1fB views", views / 1_000_000_000.0);
            } else if (views >= 1_000_000) {
                return String.format("%.1fM views", views / 1_000_000.0);
            } else if (views >= 1_000) {
                return String.format("%.1fK views", views / 1_000.0);
            } else {
                return views + " views";
            }
        } catch (NumberFormatException e) {
            return viewCount + " views";
        }
    }
    private String formatUploadDate(String uploadDate) {
        if (uploadDate == null || "Unknown".equals(uploadDate)) return "Unknown date";
        try {
            // Format: YYYYMMDD
            if (uploadDate.length() == 8) {
                String year = uploadDate.substring(0, 4);
                String month = uploadDate.substring(4, 6);
                String day = uploadDate.substring(6, 8);
                return String.format("%s-%s-%s", year, month, day);
            }
            return uploadDate;
        } catch (Exception e) {
            return uploadDate;
        }
    }
    // Health check method to verify yt-dlp is installed and working
    public boolean checkYtDlpAvailability(){
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(YT_DLP_COMMAND,"--version");
            Process process = processBuilder.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        }catch (IOException | InterruptedException e) {
            return false;
        }
    }
    // Helper method to parse size strings like "10.5MiB", "2.3GB", "512KiB"
    private double parseSize(String value, String unit) {
        try {
            double size = Double.parseDouble(value);
            switch (unit.toUpperCase()) {
                case "B": return size;
                case "KB": case "KIB": return size * 1024;
                case "MB": case "MIB": return size * 1024 * 1024;
                case "GB": case "GIB": return size * 1024 * 1024 * 1024;
                default: return size;
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    public boolean testYtDlpWithSimpleVideo() {
        Process process = null;
        try {
            String testUrl = "https://youtu.be/1sRaLqtHXQU?si=dONkHZ3gfDhDsFdY"; // First YouTube video

            ProcessBuilder processBuilder = new ProcessBuilder(
                    YT_DLP_COMMAND,
                    "--dump-json",
                    "--no-warnings",
                    testUrl
            );

            process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            boolean success = finished && exitCode == 0 && output.length() > 0;

            if (!success) {
                System.err.println("yt-dlp test failed:");
                System.err.println("Exit code: " + exitCode);
            }

            return success;

        } catch (IOException e) {
            System.err.println("IOException - yt-dlp likely not installed: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
    private DownloadProgress parseProgressLine(String line) {
        try {
            if (line.contains("[download]") && line.contains("%")) {
                Pattern pattern = Pattern.compile(
                        "\\[download\\]\\s+(\\d+\\.?\\d*)%\\s+of\\s+~?\\s*[\\d\\.,]+[KMG]?i?B?\\s+at\\s+([\\d\\.,]+[KMG]?i?B/s)\\s+ETA\\s+([\\d:]+|Unknown)"
                );

                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    double percentage = Double.parseDouble(matcher.group(1));
                    String speed = matcher.group(2);
                    String eta = matcher.group(3);

                    return new DownloadProgress(null, percentage, "Downloading", speed, eta, System.currentTimeMillis());
                }
            }

            // Handle different progress formats
            if (line.contains("[download] Downloading item") || line.contains("[download] Destination:")) {
                return new DownloadProgress(null, 0, "Starting download...", "0 KiB/s", "Unknown", System.currentTimeMillis());
            }

            if (line.contains("[download] 100%")) {
                return new DownloadProgress(null, 100, "Download completed", "0 KiB/s", "00:00", System.currentTimeMillis());
            }

        } catch (Exception e) {
            System.err.println("Error parsing progress line: " + line);
        }

        return null;
    }
}
