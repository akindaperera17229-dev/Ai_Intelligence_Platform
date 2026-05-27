package com.ai.engine.backend.dto.intelligence;

/**
 * Cycle time metric — average days from TICKET_CREATED to last update per project.
 * Used by: GET /api/intelligence/cycle-time?project=KEY
 */
public class CycleTimeDTO {

    private String project;
    private double avgDays;
    private long ticketCount;
    private double minDays;
    private double maxDays;

    public CycleTimeDTO() {}

    public CycleTimeDTO(String project, double avgDays, long ticketCount, double minDays, double maxDays) {
        this.project = project;
        this.avgDays = avgDays;
        this.ticketCount = ticketCount;
        this.minDays = minDays;
        this.maxDays = maxDays;
    }

    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }
    public double getAvgDays() { return avgDays; }
    public void setAvgDays(double avgDays) { this.avgDays = avgDays; }
    public long getTicketCount() { return ticketCount; }
    public void setTicketCount(long ticketCount) { this.ticketCount = ticketCount; }
    public double getMinDays() { return minDays; }
    public void setMinDays(double minDays) { this.minDays = minDays; }
    public double getMaxDays() { return maxDays; }
    public void setMaxDays(double maxDays) { this.maxDays = maxDays; }
}
