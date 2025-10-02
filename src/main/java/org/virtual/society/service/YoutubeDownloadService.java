package org.virtual.society.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.virtual.society.exceptions.DownloadException;
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
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class YoutubeDownloadService {

    private static final String DOWNLOAD_DIR = System.getProperty("user.dir") + "/downloads/";
    private static final String YT_DLP_COMMAND = "yt-dlp";
    private static final long PROCESS_TIMEOUT = 30;

    @Inject
    DownloadProgressService progressService;

    // Main Download Method Get Details and every thing
    public VideoInfo getVideoInfo(String videoUrl){
        try{
            String videoId = extractVideoId(videoUrl);
            if (videoId == null){
                throw new DownloadException("Invalid Youtube Url");
            }
            // Use yt-dlp to get video information
            return getVideoInfoWithYtDlp(videoUrl);
        }catch (Exception e) {
            throw new DownloadException("Failed to fetch video information", e);
        }
    }
    //Download the file method
    public File downloadVideo(String videoUrl , String formatId, String downloadId){
        try {
            progressService.updateProgress(downloadId, "starting", 0, "Extracting video ID");
            String videoId = extractVideoId(videoUrl);
            if (videoId == null){
                progressService.updateProgress(downloadId, "error", 0, "Invalid YouTube URL");
                throw new DownloadException("Invalid YouTube URL");
            }
            progressService.updateProgress(downloadId, "downloading", 10, "Creating download directory");
            //Create Directory if not exist
            Path downloadPath = Paths.get(DOWNLOAD_DIR);
            if (!Files.exists(downloadPath)){
                Files.createDirectories(downloadPath);
            }
            // Build yt-dlp command with specific format and output template
            List<String> command = new ArrayList<>();
            command.add(YT_DLP_COMMAND);
            // Add format specification
            if (formatId != null && !formatId.isEmpty() && !"best".equals(formatId)) {
                command.add("-f");
                command.add(formatId);
            }
            // Add output template with safe filename
            command.add("-o");
            command.add(DOWNLOAD_DIR + "%(title)s [%(id)s].%(ext)s");
            // Add other options to ensure proper video download
            command.add("--no-playlist");
            command.add("--merge-output-format");
            command.add("mp4");
            command.add("--no-mtime");
            command.add("--no-overwrites");
            command.add("--continue");
            // Add the video URL
            command.add(videoUrl);
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            // Redirect error stream to output stream
            processBuilder.redirectErrorStream(true);
            progressService.updateProgress(downloadId, "downloading", 20, "Starting download process");
            Process process = processBuilder.start();
            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println("yt-dlp: " + line); // Log output for debugging
                int progress = parseProgressFromOutput(line);
                if (progress >= 0) {
                    progressService.updateProgress(downloadId, "downloading", progress, "Downloading video");
                }
                if (line.contains("[Merger]")) {
                    progressService.updateProgress(downloadId, "converting", 90, "Merging formats");
                }
            }
            boolean finished = process.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                throw new DownloadException("Download timed out after " + PROCESS_TIMEOUT + " seconds");
            }
            if (process.exitValue() != 0) {
                progressService.updateProgress(downloadId, "error", 0, "Download failed");
                throw new DownloadException("Download failed: " + output.toString());
            }

            progressService.updateProgress(downloadId, "completed", 100, "Download completed successfully");
            // Extract downloaded filename from output
            String filename = extractDownloadedFilename(output.toString());
            if (filename != null) {
                File downloadedFile = new File(filename);
                if (downloadedFile.exists() && downloadedFile.length() > 0) {
                    return downloadedFile;
                } else {
                    throw new DownloadException("Downloaded file doesn't exist or is empty: " + filename);
                }
            } else {
                // Fallback: look for the most recently created file in download directory
                File latestFile = findLatestFile(downloadPath.toFile());
                if (latestFile != null && latestFile.exists() && latestFile.length() > 0) {
                    return latestFile;
                } else {
                    throw new DownloadException("Could not find downloaded file. yt-dlp output: " + output.toString());
                }
            }
        }catch (IOException | InterruptedException e) {
            progressService.updateProgress(downloadId, "error", 0, "Download error: " + e.getMessage());
            Thread.currentThread().interrupt();
            throw new DownloadException("Failed to download video", e);
        }
    }
    private String extractDownloadedFilename(String output) {
        // yt-dlp output usually contains the destination filename
        Pattern pattern = Pattern.compile("\\[download\\] Destination:\\s*(.+)");
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
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
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    YT_DLP_COMMAND,
                    "--dump-json",
                    "--no-warnings",
                    videoUrl
            );
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder jsonOutput = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null){
                jsonOutput.append(line);
            }
            boolean finished = process.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS);
            if (!finished){
                process.destroy();
                throw new DownloadException("yt-dlp command timed out");
            }
            if (process.exitValue() != 0){
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorOutput = new StringBuilder();
                while ((line = errorReader.readLine()) != null){
                    errorOutput.append(line).append("\n");
                }
                throw new DownloadException("yt-dlp failed: " + errorOutput.toString());
            }
            // Parse the JSON output (simplified parsing - in real implementation use JSON library)
            return parseYtDlpJsonOutput(jsonOutput.toString());
        }catch (IOException | InterruptedException e) {
            throw new DownloadException("Failed to execute yt-dlp command", e);
        }
    }
    private VideoInfo parseYtDlpJsonOutput(String jsonOutput){
        try{
            String id = extractFromJson(jsonOutput, "\"id\":\\s*\"([^\"]+)\"");
            String title = extractFromJson(jsonOutput, "\"title\":\\s*\"([^\"]+)\"");
            String description = extractFromJson(jsonOutput, "\"description\":\\s*\"([^\"]+)\"");
            String thumbnail = extractFromJson(jsonOutput, "\"thumbnail\":\\s*\"([^\"]+)\"");
            String duration = extractFromJson(jsonOutput, "\"duration\":\\s*([0-9]+)");
            String viewCount = extractFromJson(jsonOutput, "\"view_count\":\\s*([0-9]+)");
            String uploadDate = extractFromJson(jsonOutput, "\"upload_date\":\\s*\"([^\"]+)\"");
            // Parse formats
            List<VideoFormat> formats = parseFormatsFromJson(jsonOutput);
            // Format duration
            String formattedDuration = formatDuration(duration);
            // Format view count
            String formattedViews = formatViews(viewCount);
            // Format upload date
            String formattedUploadDate = formatUploadDate(uploadDate);
            return new VideoInfo(id,title,description,thumbnail,formattedDuration,formattedViews,formattedUploadDate,formats);
        } catch (Exception e) {
            throw new DownloadException("Failed to parse yt-dlp output", e);
        }
    }
    private List<VideoFormat> parseFormatsFromJson(String jsonOutput) {
        List<VideoFormat> formats = new ArrayList<>();

        // Extract formats section using regex
        Pattern formatPattern = Pattern.compile(
                "\"format_id\":\\s*\"([^\"]+)\".*?" +
                        "\"ext\":\\s*\"([^\"]+)\".*?" +
                        "\"width\":\\s*(\\d+).*?" +
                        "\"height\":\\s*(\\d+).*?" +
                        "\"fps\":\\s*(\\d+).*?" +
                        "\"vcodec\":\\s*\"([^\"]+)\".*?" +
                        "\"acodec\":\\s*\"([^\"]+)\".*?" +
                        "\"filesize\":\\s*(\\d+)",
                Pattern.DOTALL
        );

        Matcher matcher = formatPattern.matcher(jsonOutput);

        while (matcher.find()) {
            String formatId = matcher.group(1);
            String extension = matcher.group(2);
            int width = Integer.parseInt(matcher.group(3));
            int height = Integer.parseInt(matcher.group(4));
            int fps = Integer.parseInt(matcher.group(5));
            String videoCodec = matcher.group(6);
            String audioCodec = matcher.group(7);
            String fileSize = matcher.group(8);

            String quality = height + "p";
            String formatType = "Video";
            String codec = videoCodec;
            String size = formatFileSize(fileSize);

            formats.add(new VideoFormat(formatId, quality, formatType, size, fps, codec, audioCodec));
        }

        // Also extract audio-only formats
        Pattern audioPattern = Pattern.compile(
                "\"format_id\":\\s*\"([^\"]+)\".*?" +
                        "\"ext\":\\s*\"([^\"]+)\".*?" +
                        "\"acodec\":\\s*\"([^\"]+)\".*?" +
                        "\"asr\":\\s*(\\d+).*?" +
                        "\"filesize\":\\s*(\\d+)",
                Pattern.DOTALL
        );

        Matcher audioMatcher = audioPattern.matcher(jsonOutput);

        while (audioMatcher.find()) {
            String formatId = audioMatcher.group(1);
            String extension = audioMatcher.group(2);
            String audioCodec = audioMatcher.group(3);
            String sampleRate = audioMatcher.group(4);
            String fileSize = audioMatcher.group(5);

            String quality = "Audio Only";
            String formatType = extension.toUpperCase();
            String size = formatFileSize(fileSize);
            String bitrate = sampleRate != null ? (Integer.parseInt(sampleRate) / 1000) + " kHz" : "Unknown";

            formats.add(new VideoFormat(formatId, quality, formatType, size, 0, null, bitrate));
        }

        return formats;
    }
    private String extractFromJson(String json, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "Unknown";
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

    private int parseProgressFromOutput(String line) {
        if (line == null || line.trim().isEmpty()) {
            return -1;
        }

        try {
            // Check for common completion indicators first
            if (line.contains("100%") ||
                    line.contains("Download completed") ||
                    line.contains("already been downloaded")) {
                return 100;
            }

            // Pattern 1: Standard progress percentage [download]  12.5% of 10.01MiB
            Pattern percentagePattern = Pattern.compile("\\[download\\].*?(\\d+\\.?\\d*)%");
            Matcher percentageMatcher = percentagePattern.matcher(line);
            if (percentageMatcher.find()) {
                float progress = Float.parseFloat(percentageMatcher.group(1));
                return Math.min(100, Math.max(0, (int) progress));
            }

            // Pattern 2: Fractional progress [download] Downloading item 3 of 5
            Pattern fractionPattern = Pattern.compile("\\[download\\].*?(\\d+)\\s+of\\s+(\\d+)");
            Matcher fractionMatcher = fractionPattern.matcher(line);
            if (fractionMatcher.find()) {
                int current = Integer.parseInt(fractionMatcher.group(1));
                int total = Integer.parseInt(fractionMatcher.group(2));
                if (total > 0 && current <= total) {
                    return (int) ((current * 100.0) / total);
                }
            }

            // Pattern 3: File size progress [download]  5.2MiB of 10.1MiB
            Pattern sizePattern = Pattern.compile("\\[download\\].*?(\\d+\\.?\\d*)([KMG]?i?B)\\s+of\\s+(\\d+\\.?\\d*)([KMG]?i?B)");
            Matcher sizeMatcher = sizePattern.matcher(line);
            if (sizeMatcher.find()) {
                double currentSize = parseSize(sizeMatcher.group(1), sizeMatcher.group(2));
                double totalSize = parseSize(sizeMatcher.group(3), sizeMatcher.group(4));
                if (totalSize > 0) {
                    int progress = (int) ((currentSize * 100.0) / totalSize);
                    return Math.min(100, progress);
                }
            }

            // Pattern 4: Processing stages
            if (line.contains("[Merger]")) {
                return 90; // Merging formats
            }
            if (line.contains("[ExtractAudio]")) {
                return 95; // Extracting audio
            }
            if (line.contains("[Metadata]")) {
                return 97; // Adding metadata
            }
            if (line.contains("[info]")) {
                return 99; // Final info messages
            }

            // Pattern 5: Error messages
            if (line.contains("ERROR") || line.contains("error") || line.contains("failed")) {
                return -2; // Special value to indicate error
            }

        } catch (Exception e) {
            System.err.println("Error parsing progress from line: " + line + " - " + e.getMessage());
        }

        return -1;
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
}
