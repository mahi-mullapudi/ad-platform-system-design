package com.tutorialq.systemdesign.adclick.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "adId is required")
    @Size(max = 255, message = "adId must be at most 255 characters")
    @Column("ad_id")
    private String adId;

    @NotBlank(message = "campaignId is required")
    @Size(max = 255, message = "campaignId must be at most 255 characters")
    @Column("campaign_id")
    private String campaignId;

    @Size(max = 255, message = "userId must be at most 255 characters")
    @Column("user_id")
    private String userId;

    @Column("event_type")
    private String eventType; // CLICK, IMPRESSION — set by controller

    @Column("timestamp")
    private Instant timestamp;

    @Size(max = 45, message = "ipAddress must be at most 45 characters")
    @Column("ip_address")
    private String ipAddress;

    @Size(max = 1024, message = "userAgent must be at most 1024 characters")
    @Column("user_agent")
    private String userAgent;

    @Column("metadata")
    private String metadata; // JSONB column for unstructured data

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
