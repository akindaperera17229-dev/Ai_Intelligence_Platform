package com.ai.engine.backend.dto.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Lightweight DTO for deserializing Jira Cloud REST API project list response.
 * Maps GET /rest/api/3/project/search
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraProjectDTO {

    @JsonProperty("id")
    private String id;

    @JsonProperty("key")
    private String key;

    @JsonProperty("name")
    private String name;

    @JsonProperty("projectTypeKey")
    private String projectTypeKey;

    @JsonProperty("avatarUrls")
    private java.util.Map<String, String> avatarUrls;

    // --- Getters & Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProjectTypeKey() { return projectTypeKey; }
    public void setProjectTypeKey(String projectTypeKey) { this.projectTypeKey = projectTypeKey; }

    public java.util.Map<String, String> getAvatarUrls() { return avatarUrls; }
    public void setAvatarUrls(java.util.Map<String, String> avatarUrls) { this.avatarUrls = avatarUrls; }

    @Override
    public String toString() {
        return "JiraProjectDTO{key='" + key + "', name='" + name + "'}";
    }

    /**
     * Wrapper for Jira paginated project search response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageResponse {

        @JsonProperty("values")
        private List<JiraProjectDTO> values;

        @JsonProperty("total")
        private int total;

        @JsonProperty("startAt")
        private int startAt;

        @JsonProperty("maxResults")
        private int maxResults;

        @JsonProperty("isLast")
        private boolean isLast;

        public List<JiraProjectDTO> getValues() { return values; }
        public void setValues(List<JiraProjectDTO> values) { this.values = values; }

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }

        public int getStartAt() { return startAt; }
        public void setStartAt(int startAt) { this.startAt = startAt; }

        public int getMaxResults() { return maxResults; }
        public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

        public boolean isLast() { return isLast; }
        public void setLast(boolean last) { isLast = last; }
    }
}
