package com.ai.engine.backend.dto.intelligence;

/**
 * Single data point in a velocity trend chart.
 * Represents event count for one source on one day.
 * Used by: GET /api/intelligence/velocity?days=14
 */
public class VelocityDataPointDTO {

    private String date;      // ISO date string: "2026-05-21"
    private String source;    // GITHUB / JIRA / SLACK
    private long count;       // number of events on that day

    public VelocityDataPointDTO() {}

    public VelocityDataPointDTO(String date, String source, long count) {
        this.date = date;
        this.source = source;
        this.count = count;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
