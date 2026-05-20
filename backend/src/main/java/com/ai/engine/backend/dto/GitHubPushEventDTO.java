package com.ai.engine.backend.dto;

import lombok.Data;

@Data
public class GitHubPushEventDTO {

    private String ref;
    private Repository repository;
    private Pusher pusher;

    @Data
    public static class Repository {
        private String name;
    }

    @Data
    public static class Pusher {
        private String name;
    }
    
}
