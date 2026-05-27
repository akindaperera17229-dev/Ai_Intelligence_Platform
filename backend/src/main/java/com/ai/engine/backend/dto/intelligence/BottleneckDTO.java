package com.ai.engine.backend.dto.intelligence;

import java.time.LocalDateTime;

/**
 * Bottleneck — an event that has been stuck (not updated) beyond a threshold.
 * Covers: Jira tickets stuck in status, GitHub PRs stale in review, Slack threads gone dark.
 * Used by: GET /api/intelligence/bottlenecks?stuckDays=3
 */
public class BottleneckDTO {

    private String source;           // JIRA / GITHUB / SLACK
    private String eventType;        // TICKET_UPDATED / CODE_PUSH / MESSAGE_SENT
    private String engineerName;
    private String summary;
    private String ticketOrRef;      // Jira ticket key / PR number / branch
    private String project;
    private LocalDateTime lastSeen;
    private long daysSinceLastUpdate;

    public BottleneckDTO() {}

    public BottleneckDTO(String source, String eventType, String engineerName,
                         String summary, String ticketOrRef, String project,
                         LocalDateTime lastSeen, long daysSinceLastUpdate) {
        this.source = source;
        this.eventType = eventType;
        this.engineerName = engineerName;
        this.summary = summary;
        this.ticketOrRef = ticketOrRef;
        this.project = project;
        this.lastSeen = lastSeen;
        this.daysSinceLastUpdate = daysSinceLastUpdate;
    }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getEngineerName() { return engineerName; }
    public void setEngineerName(String engineerName) { this.engineerName = engineerName; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getTicketOrRef() { return ticketOrRef; }
    public void setTicketOrRef(String ticketOrRef) { this.ticketOrRef = ticketOrRef; }
    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }
    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }
    public long getDaysSinceLastUpdate() { return daysSinceLastUpdate; }
    public void setDaysSinceLastUpdate(long daysSinceLastUpdate) { this.daysSinceLastUpdate = daysSinceLastUpdate; }
}
