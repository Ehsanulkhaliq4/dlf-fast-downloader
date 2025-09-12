package org.virtual.society.model;

public class VideoFormat {
    private String id;
    private String quality;
    private String format;
    private String size;
    private int fps;
    private String codec;
    private String bitrate;

    // Constructors
    public VideoFormat() {}

    public VideoFormat(String id, String quality, String format, String size, int fps, String codec, String bitrate) {
        this.id = id;
        this.quality = quality;
        this.format = format;
        this.size = size;
        this.fps = fps;
        this.codec = codec;
        this.bitrate = bitrate;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getQuality() { return quality; }
    public void setQuality(String quality) { this.quality = quality; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public int getFps() { return fps; }
    public void setFps(int fps) { this.fps = fps; }

    public String getCodec() { return codec; }
    public void setCodec(String codec) { this.codec = codec; }

    public String getBitrate() { return bitrate; }
    public void setBitrate(String bitrate) { this.bitrate = bitrate; }
}
