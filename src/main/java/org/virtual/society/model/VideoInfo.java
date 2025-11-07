package org.virtual.society.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
@RegisterForReflection
public class VideoInfo {
    private String id;
    private String title;
    private String description;
    private String thumbnail;
    private String duration;
    private String views;
    private String uploadDate;
    private List<VideoFormat> formats;

    // Constructors
    public VideoInfo() {}

    public VideoInfo(String id, String title, String description, String thumbnail,
                     String duration, String views, String uploadDate, List<VideoFormat> formats) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.thumbnail = thumbnail;
        this.duration = duration;
        this.views = views;
        this.uploadDate = uploadDate;
        this.formats = formats;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getViews() { return views; }
    public void setViews(String views) { this.views = views; }

    public String getUploadDate() { return uploadDate; }
    public void setUploadDate(String uploadDate) { this.uploadDate = uploadDate; }

    public List<VideoFormat> getFormats() { return formats; }
    public void setFormats(List<VideoFormat> formats) { this.formats = formats; }
}
