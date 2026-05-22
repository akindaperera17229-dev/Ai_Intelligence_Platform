package com.ai.engine.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class GitHubPushEventDTO {

    private String ref;
    private Repository repository;
    private Pusher pusher;
    private List<Commit> commits;

    @Data
    public static class Repository {
        private String name;
    }

    @Data
    public static class Pusher {
        private String name;
    }
    
    @Data
    public static class Commit {
        private String id;
        private String message;
    }
}
