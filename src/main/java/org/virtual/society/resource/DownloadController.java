package org.virtual.society.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.virtual.society.model.DownloadProgress;
import org.virtual.society.model.DownloadRequest;
import org.virtual.society.model.VideoInfo;
import org.virtual.society.service.DownloadProgressService;
import org.virtual.society.service.YoutubeDownloadService;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Path("/api/download")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DownloadController {

    @Inject
    YoutubeDownloadService downloadService;

    @Inject
    DownloadProgressService progressService;

    // Store active downloads
    private final ConcurrentHashMap<String, CompletableFuture<File>> activeDownloads = new ConcurrentHashMap<>();

    private VideoInfo videoInfo = new VideoInfo();


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
    public void getVideoInfo(@QueryParam("url") String url , @Suspended final AsyncResponse asyncResponse) {
        new Thread(() -> {
            try {
                boolean ytDlpWorking = downloadService.testYtDlpWithSimpleVideo();
                if (!ytDlpWorking) {
                    asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("{\"error\": \"yt-dlp is not working properly\"}")
                            .build());
                    return;
                }

                videoInfo = downloadService.getVideoInfo(url);
                asyncResponse.resume(Response.ok(videoInfo).build());
            } catch (Exception e) {
                asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("{\"error\": \"" + e.getMessage() + "\"}")
                        .build());
            }
        }).start();
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
            CompletableFuture<File> downloadFuture = downloadService.downloadVideo(
                    request.getUrl(),
                    request.getFormatId(),
                    downloadId
            );
            activeDownloads.put(downloadId, downloadFuture);
            // Clean up when completed
            downloadFuture.whenComplete((result, throwable) -> {
                activeDownloads.remove(downloadId);
                if (throwable != null) {
                    System.err.println("Download failed for " + downloadId + ": " + throwable.getMessage());
                }
            });
            Map<String, String> response = new HashMap<>();
            response.put("downloadId", downloadId);
            response.put("status", "started");
            System.out.println(response );
            return Response.ok() .entity(response).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/progress/{downloadId}")
    public Response getProgress(@PathParam("downloadId") String  downloadId){
      try {
          DownloadProgress progress = progressService.getProgress(downloadId);
          if (progress == null) {
              return Response.status(Response.Status.NOT_FOUND)
                      .entity("{\"error\": \"Download not found\"}")
                      .build();
          }
          if (progress.getPercentage() >= 100 &&
                  System.currentTimeMillis() - progress.getLastUpdate() > 30000) { // 30 seconds
              progressService.removeProgress(downloadId);
          }
          return Response.ok(progress).build();
      }catch (Exception e){
          return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                  .entity("{\"error\": \"" + e.getMessage() + "\"}")
                  .build();
      }
    }
}
