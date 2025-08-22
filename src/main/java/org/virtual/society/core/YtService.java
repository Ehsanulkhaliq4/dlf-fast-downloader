package org.virtual.society.core;

import io.smallrye.mutiny.Multi;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;

public class YtService {
    private final Path baseDir;

    public YtService(Path baseDir) throws IOException {
        this.baseDir = baseDir;
        Files.createDirectories(baseDir);
    }

    public JobStore.Job startDownload(String url,String fmt,String fileName) throws IOException{
        String jobId = UUID.randomUUID().toString();
        JobStore.Job job = new JobStore.Job();
        job.id = jobId;

        String safeName = (fileName == null || fileName.isBlank()) ? "video_" + Instant.now().toEpochMilli() : fileName;
        // yt-dlp will figure out extension via ffmpeg/remixing
        String outTpl = safeName + ".%(ext)s";
        Path outPath = baseDir.resolve(outTpl);
        job.fileName = outTpl;
        job.outPath = outPath.toString();
        JobStore.JOBS.put(jobId,job);
        new Thread(()->runYt(job , url , fmt , outTpl));
        return job;
    }

    private void runYt(JobStore.Job job,String url,String fmt,String outTpl){
        job.status = "RUNNING";
        try {
            ProcessBuilder pb = new ProcessBuilder()
                    .command("yt-dlp","-f",(fmt == null || fmt.isBlank() ? "best" : fmt) , "-o", outTpl , "--no-playlist","--merge-output-format", "mp4") // ensure common container
                    .directory(new File(baseDir.toString()));
            Process p = pb.start();
            try(BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream()))){
                String line;
                while ((line = br.readLine()) != null){/*Could persist progress*/}
                while ((line = er.readLine()) != null){/*Could persist error*/}
            }
            int code = p.waitFor();
            if (code == 0){
                job.status = "DONE";
                job.msg = "OK";
            }else {
                job.status = "ERROR";
                job.msg = "OK";
            }
        }catch (Exception e){
            job.status = "ERROR";
            job.msg = e.getMessage();
        }
    }

    public Multi<String> progressStream(String jobId){
        return Multi.createFrom().ticks().every(java.time.Duration.ofMillis(700))
                .onOverflow().drop()
                .map(t -> {
                    JobStore.Job j = JobStore.JOBS.get(jobId);
                    if (j == null) return "UNKNOWN";
                    return "status="+ j.status + "|message="+j.msg;
                })
                .select().where(msg -> {
                    JobStore.Job j = JobStore.JOBS.get(jobId);
                    return j!=null && !"DONE".equals(j.status) && !"ERROR".equals(j.status);
                });
    }
}
