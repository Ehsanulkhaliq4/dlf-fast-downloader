package org.virtual.society.resource;

import io.smallrye.mutiny.Multi;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import jakarta.ws.rs.*;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.virtual.society.core.JobStore;
import org.virtual.society.core.YtService;
import java.io.File;
import java.nio.file.Paths;
import jakarta.ws.rs.core.*;
import org.virtual.society.dto.DownloadRequest;
import org.virtual.society.dto.DownloadResponse;

@Path("/api")
@Singleton
@Produces(MediaType.APPLICATION_JSON)
public class DownloadResource {

    private YtService yt;
    private java.nio.file.Path baseDir;

    @PostConstruct
    void init(){
        String dir = System.getProperty("app.download-dir",
                System.getenv().getOrDefault("APP_DOWNLOAD_DIR","/temp/yt-downloads"));
        baseDir = Paths.get(dir);
        try { yt = new YtService(baseDir);} catch (Exception e){throw  new RuntimeException(e);}
    }

    @POST
    @Path("/download")
    @Consumes(MediaType.APPLICATION_JSON)
    public DownloadResponse start(DownloadRequest request) throws Exception {
        if (request == null || request.url == null || request.url.isBlank()){
            throw new BadRequestException("url is required");
        }
        var job = yt.startDownload(request.url, request.format, request.filename);
        var res = new DownloadResponse();
        res.jobId = job.id;
        res.filename = job.fileName;
        res.message = "QUEUED";
        res.status = job.status;
        return res;
    }
    @GET @Path("/jobs/{id}")
    public DownloadResponse status(@PathParam("id") String id){
        var job = JobStore.JOBS.get(id);
        if (job == null) throw new NotFoundException();
        var res = new DownloadResponse();
        res.jobId = job.id; res.filename = job.fileName; res.status = job.status; res.message = job.msg;
        return res;
    }

    // Simple SSE that pushes status until completion (optional on frontend)
    @GET @Path("/jobs/{id}/progress")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> progress(@PathParam("id") String id){
        return yt.progressStream(id);
    }

    // When status == DONE, fetch the file
    @GET @Path("/jobs/{id}/file")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response file(@PathParam("id") String id , @Context Request request){
       var j =  JobStore.JOBS.get(id);
        if (j == null) throw new NotFoundException();
        if(!"DONE".equals(j.status)) throw new ClientErrorException("Not Ready",425);
        // Match the actual created file name (globbing safeName.*)
        File[] files = baseDir.toFile().listFiles((dir, name) -> name.startsWith(j.fileName.substring(0, j.fileName.indexOf(".%("))));
        if (files == null || files.length == 0) throw new NotFoundException("File not yet found");
        File f = files[0];
        return Response.ok(f,MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + f.getName() + "\"").build();
    }
}
