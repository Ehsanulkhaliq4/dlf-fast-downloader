package org.virtual.society.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.virtual.society.exceptions.DownloadException;
import org.virtual.society.model.VideoFormat;
import org.virtual.society.model.VideoInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class YoutubeDownloadService {

    private static final String DOWNLOAD_DIR = System.getProperty("user.dir") + "/downloads/";
    private static final String YT_DLP_COMMAND = "yt-dlp";
    private static final long PROCESS_TIMEOUT = 30;

    public VideoInfo getVideoInfo(String videoUrl){
        try{
            String videoId = extractVideoId(videoUrl);
            if (videoId == null){
                throw new DownloadException("Invalid Youtube Url");
            }


        }
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
            
        }
    }
}
