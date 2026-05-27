package com.ai.engine.backend.dto.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Lightweight DTO for deserializing a Jira Cloud issue from REST API.
 * Maps GET /rest/api/3/issue/{issueKey} and the issues array in search results.
 * Only maps curated fields — everything else goes into rawPayload / JSONB metadata.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssueDTO {

    @JsonProperty("id")
    private String id;

    @JsonProperty("key")
    private String key;

    @JsonProperty("fields")
    private Fields fields;

    // --- Nested Fields ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fields {

        @JsonProperty("summary")
        private String summary;

        @JsonProperty("status")
        private StatusField status;

        @JsonProperty("priority")
        private NameField priority;

        @JsonProperty("assignee")
        private UserField assignee;

        @JsonProperty("reporter")
        private UserField reporter;

        @JsonProperty("project")
        private ProjectField project;

        @JsonProperty("created")
        private String created;

        @JsonProperty("updated")
        private String updated;

        @JsonProperty("story_points")
        private Double storyPoints;

        // story points can also live under customfield_10016 in Jira
        @JsonProperty("customfield_10016")
        private Double storyPointsAlt;

        @JsonProperty("sprint")
        private Object sprint; // Sprint can be array or object depending on Jira config

        public Double getResolvedStoryPoints() {
            if (storyPoints != null) return storyPoints;
            if (storyPointsAlt != null) return storyPointsAlt;
            return null;
        }

        // --- Getters & Setters ---
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public StatusField getStatus() { return status; }
        public void setStatus(StatusField status) { this.status = status; }
        public NameField getPriority() { return priority; }
        public void setPriority(NameField priority) { this.priority = priority; }
        public UserField getAssignee() { return assignee; }
        public void setAssignee(UserField assignee) { this.assignee = assignee; }
        public UserField getReporter() { return reporter; }
        public void setReporter(UserField reporter) { this.reporter = reporter; }
        public ProjectField getProject() { return project; }
        public void setProject(ProjectField project) { this.project = project; }
        public String getCreated() { return created; }
        public void setCreated(String created) { this.created = created; }
        public String getUpdated() { return updated; }
        public void setUpdated(String updated) { this.updated = updated; }
        public Double getStoryPoints() { return storyPoints; }
        public void setStoryPoints(Double storyPoints) { this.storyPoints = storyPoints; }
        public Double getStoryPointsAlt() { return storyPointsAlt; }
        public void setStoryPointsAlt(Double storyPointsAlt) { this.storyPointsAlt = storyPointsAlt; }
        public Object getSprint() { return sprint; }
        public void setSprint(Object sprint) { this.sprint = sprint; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatusField {
        @JsonProperty("name")
        private String name;
        @JsonProperty("statusCategory")
        private StatusCategory statusCategory;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public StatusCategory getStatusCategory() { return statusCategory; }
        public void setStatusCategory(StatusCategory c) { this.statusCategory = c; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatusCategory {
        @JsonProperty("key")
        private String key; // "new", "indeterminate", "done"
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NameField {
        @JsonProperty("name")
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserField {
        @JsonProperty("accountId")
        private String accountId;
        @JsonProperty("displayName")
        private String displayName;
        @JsonProperty("emailAddress")
        private String emailAddress;
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getEmailAddress() { return emailAddress; }
        public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProjectField {
        @JsonProperty("key")
        private String key;
        @JsonProperty("name")
        private String name;
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /**
     * Paginated search result wrapper.
     * Maps GET /rest/api/3/search
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResult {
        @JsonProperty("issues")
        private List<JiraIssueDTO> issues;
        @JsonProperty("total")
        private int total;
        @JsonProperty("startAt")
        private int startAt;
        @JsonProperty("maxResults")
        private int maxResults;

        public List<JiraIssueDTO> getIssues() { return issues; }
        public void setIssues(List<JiraIssueDTO> issues) { this.issues = issues; }
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getStartAt() { return startAt; }
        public void setStartAt(int startAt) { this.startAt = startAt; }
        public int getMaxResults() { return maxResults; }
        public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
    }

    // --- Root Getters & Setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public Fields getFields() { return fields; }
    public void setFields(Fields fields) { this.fields = fields; }
}
