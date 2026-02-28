package com.tutorialq.systemdesign.processor.model;

import java.io.Serializable;

public class ClickAggregation implements Serializable {

    private String campaignId;
    private String eventType;
    private long count;
    private long windowStart;
    private long windowEnd;

    public ClickAggregation() {}

    public ClickAggregation(String campaignId, String eventType, long count, long windowStart, long windowEnd) {
        this.campaignId = campaignId;
        this.eventType = eventType;
        this.count = count;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }

    public long getWindowStart() { return windowStart; }
    public void setWindowStart(long windowStart) { this.windowStart = windowStart; }

    public long getWindowEnd() { return windowEnd; }
    public void setWindowEnd(long windowEnd) { this.windowEnd = windowEnd; }

    @Override
    public String toString() {
        return "ClickAggregation{campaignId='" + campaignId + "', eventType='" + eventType +
                "', count=" + count + ", windowStart=" + windowStart + ", windowEnd=" + windowEnd + "}";
    }
}
