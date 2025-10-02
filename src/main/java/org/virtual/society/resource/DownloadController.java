package org.virtual.society.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.virtual.society.model.DownloadRequest;
import org.virtual.society.model.DownloadStatus;
import org.virtual.society.model.VideoInfo;
import org.virtual.society.service.DownloadProgressService;
import org.virtual.society.service.YoutubeDownloadService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Path("/api/download")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DownloadController {

    @Inject
    YoutubeDownloadService downloadService;

    @Inject
    DownloadProgressService progressService;

    @GET
    @Path("/health")
    public Response checkHealth(){
        boolean ytDlpAvailable = downloadService.checkYtDlpAvailability();
        if (ytDlpAvailable){
            return Response.ok().entity("{\"status\": \"healthy\", \"yt-dlp\": \"available\"}").build();
        } else {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("{\"status\": \"unhealthy\", \"yt-dlp\": \"not available\"}")
                    .build();
        }
    }

    @GET
    @Path("/info")
    public Response getVideoInfo(@QueryParam("url") String url) {
        try {
            VideoInfo videoInfo = downloadService.getVideoInfo(url);
            return Response.ok(videoInfo).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
    @GET
    @Path("/status/{jobId}")
    public Response getDownloadStatus(@PathParam("jobId") String jobId) {
        // In a real implementation, you would track download status
        return Response.ok()
                .entity("{\"status\": \"completed\", \"jobId\": \"" + jobId + "\"}")
                .build();
    }

    @POST
    @Path("/request")
    public Response downloadVideo(DownloadRequest request){
        try {
            String downloadId = UUID.randomUUID().toString();
            CompletableFuture.runAsync(()->{
                downloadService.downloadVideo(request.getUrl(),request.getFormatId(),downloadId);
            });
//            File videoFile = downloadService.downloadVideo(request.getUrl(), request.getFormatId());
            Map<String, String> response = new HashMap<>();
            response.put("downloadId", downloadId);
            response.put("status", "started");
            return Response.ok() .entity("{\"message\": \"Download completed\", \"filename\": \"" + response + "\"}").build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    @GET
    @Path("/progress/{downloadId}")
    public Response getProgress(@PathParam("downloadId") String  downloadId){
        DownloadStatus status = progressService.getProgress(downloadId);
        return Response.ok(status).build();
    }
}
