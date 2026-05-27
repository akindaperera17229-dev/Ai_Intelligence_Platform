package com.ai.engine.backend.dto.intelligence;

/**
 * Per-engineer workload breakdown across all connected platforms.
 * Used by: GET /api/intelligence/workload
 */
public class WorkloadDTO {

    private String engineerName;
    private long githubEvents;     // CODE_PUSH, PR_OPENED, etc.
    private long jiraTickets;      // TICKET_CREATED, TICKET_UPDATED
    private long slackMessages;    // MESSAGE_SENT
    private long totalEvents;      // sum across all platforms

    public WorkloadDTO() {}

    public WorkloadDTO(String engineerName) {
        this.engineerName = engineerName;
        this.githubEvents = 0;
        this.jiraTickets = 0;
        this.slackMessages = 0;
        this.totalEvents = 0;
    }

    public WorkloadDTO(String engineerName, long githubEvents, long jiraTickets,
                       long slackMessages) {
        this.engineerName = engineerName;
        this.githubEvents = githubEvents;
        this.jiraTickets = jiraTickets;
        this.slackMessages = slackMessages;
        this.totalEvents = githubEvents + jiraTickets + slackMessages;
    }

    public void addEvents(String source, long count) {
        switch (source.toUpperCase()) {
            case "GITHUB" -> { githubEvents += count; totalEvents += count; }
            case "JIRA"   -> { jiraTickets += count; totalEvents += count; }
            case "SLACK"  -> { slackMessages += count; totalEvents += count; }
            default       -> totalEvents += count;
        }
    }

    public String getEngineerName() { return engineerName; }
    public void setEngineerName(String engineerName) { this.engineerName = engineerName; }
    public long getGithubEvents() { return githubEvents; }
    public void setGithubEvents(long githubEvents) { this.githubEvents = githubEvents; }
    public long getJiraTickets() { return jiraTickets; }
    public void setJiraTickets(long jiraTickets) { this.jiraTickets = jiraTickets; }
    public long getSlackMessages() { return slackMessages; }
    public void setSlackMessages(long slackMessages) { this.slackMessages = slackMessages; }
    public long getTotalEvents() { return totalEvents; }
    public void setTotalEvents(long totalEvents) { this.totalEvents = totalEvents; }
}
