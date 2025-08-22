package org.virtual.society.core;

import java.util.concurrent.ConcurrentHashMap;

public class JobStore {
    public static class Job{
        public String id;
        public String fileName;
        public String status="QUEUED";
        public String msg = "";
        public String outPath;
    }
    public static final ConcurrentHashMap<String,Job> JOBS = new ConcurrentHashMap<>();
}
