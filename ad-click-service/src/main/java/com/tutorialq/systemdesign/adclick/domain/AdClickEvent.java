package com.tutorialq.systemdesign.adclick.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ad_click_events")
public class AdClickEvent {

    @Id
    private Long id;

    @Column("event_id")
    private UUID eventId;

    @Column("ad_id")
    private String adId;

    @Column("campaign_id")
    private String campaignId;

    @Column("user_id")
    private String userId;

    @Column("event_type")
    private String eventType; // CLICK, IMPRESSION

    @Column("timestamp")
    private Instant timestamp;

    @Column("ip_address")
    private String ipAddress;

    @Column("user_agent")
    private String userAgent;

    @Column("metadata")
    private String metadata; // JSONB column for unstructured data

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
