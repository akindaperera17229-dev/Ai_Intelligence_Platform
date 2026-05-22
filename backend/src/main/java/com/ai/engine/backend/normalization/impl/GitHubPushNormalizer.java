package com.ai.engine.backend.normalization.impl;

import com.ai.engine.backend.dto.GitHubPushEventDTO;
import com.ai.engine.backend.model.Engineer;
import com.ai.engine.backend.model.EngineeringEvent;
import com.ai.engine.backend.normalization.EventNormalizer;
import com.ai.engine.backend.service.IdentityResolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GitHubPushNormalizer implements EventNormalizer {

    private final ObjectMapper objectMapper;
    private final IdentityResolutionService identityResolutionService;

    public GitHubPushNormalizer(ObjectMapper objectMapper, IdentityResolutionService identityResolutionService) {
        this.objectMapper = objectMapper;
        this.identityResolutionService = identityResolutionService;
    }

    @Override
    public boolean supports(String source, String eventType) {
        return "GITHUB".equalsIgnoreCase(source) && "CODE_PUSH".equalsIgnoreCase(eventType);
    }

    @Override
    public EngineeringEvent normalize(String rawPayload) {
        EngineeringEvent event = new EngineeringEvent();
        event.setSource("GITHUB");
        event.setEventType("CODE_PUSH");
        event.setRawPayload(rawPayload);
        event.setIngestionSource("WEBHOOK");
        event.setTimestamp(LocalDateTime.now());

        try {
            GitHubPushEventDTO dto = objectMapper.readValue(rawPayload, GitHubPushEventDTO.class);

            // Safe Null Checks for Pusher
            String pusherName = "Unknown Pusher";
            if (dto.getPusher() != null && dto.getPusher().getName() != null) {
                pusherName = dto.getPusher().getName();
            }
            event.setEngineerName(pusherName);
            event.setUserId(pusherName);

            // Resolve engineer profile
            Engineer engineer = identityResolutionService.resolveEngineer("GITHUB", pusherName, pusherName);
            event.setEngineer(engineer);

            // Safe Null Checks for Ref / Branch
            String branch = "unknown-branch";
            if (dto.getRef() != null) {
                branch = dto.getRef().replace("refs/heads/", "");
            }
            event.setBranchName(branch);

            // Safe Null Checks for Repository
            String repoName = "unknown-repository";
            if (dto.getRepository() != null && dto.getRepository().getName() != null) {
                repoName = dto.getRepository().getName();
            }
            event.setRepositoryName(repoName);

            // Safe Null Checks for Commits
            int commitCount = 0;
            List<Map<String, String>> commitData = new ArrayList<>();
            if (dto.getCommits() != null) {
                commitCount = dto.getCommits().size();
                for (GitHubPushEventDTO.Commit commit : dto.getCommits()) {
                    if (commit != null) {
                        Map<String, String> c = new HashMap<>();
                        c.put("id", commit.getId());
                        c.put("message", commit.getMessage());
                        commitData.add(c);
                    }
                }
            }
            event.setCommitCount(commitCount);

            // Build Summary
            String summary = pusherName + " pushed " + commitCount + " commit(s) to " + branch + " in " + repoName;
            event.setSummary(summary);

            // Build Metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("branch", branch);
            metadata.put("repository", repoName);
            metadata.put("commits", commitData);
            metadata.put("pusher", pusherName);
            event.setMetadata(metadata);

        } catch (Exception e) {
            // Soft fail parsing, set error details but don't crash
            event.setSummary("Failed to parse GitHub Push Event: " + e.getMessage());
            event.setCommitCount(0);
            event.setBranchName("error");
            event.setRepositoryName("error");
        }

        return event;
    }
}
