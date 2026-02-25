package com.tutorialq.systemdesign.adclick.repository;

import com.tutorialq.systemdesign.adclick.domain.AdClickEvent;
import com.tutorialq.systemdesign.adclick.domain.CampaignSummary;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AdClickEventRepository extends ReactiveCrudRepository<AdClickEvent, Long> {

    Mono<AdClickEvent> findByEventId(UUID eventId);

    @Query("SELECT * FROM ad_click_events WHERE campaign_id = :campaignId AND timestamp >= :startTime AND timestamp < :endTime ORDER BY timestamp DESC")
    Flux<AdClickEvent> findByCampaignIdAndTimeRange(String campaignId, Instant startTime, Instant endTime);

    @Query("SELECT * FROM ad_click_events WHERE ad_id = :adId AND timestamp >= :startTime AND timestamp < :endTime ORDER BY timestamp DESC")
    Flux<AdClickEvent> findByAdIdAndTimeRange(String adId, Instant startTime, Instant endTime);

    @Query("SELECT COUNT(*) FROM ad_click_events WHERE campaign_id = :campaignId AND event_type = :eventType AND timestamp >= :startTime")
    Mono<Long> countByCampaignIdAndEventType(String campaignId, String eventType, Instant startTime);

    @Query("SELECT COUNT(*) FROM ad_click_events WHERE campaign_id = :campaignId AND event_type = :eventType AND timestamp >= :startTime AND timestamp < :endTime")
    Mono<Long> countByCampaignIdAndEventTypeInTimeRange(String campaignId, String eventType, Instant startTime, Instant endTime);

    @Query("SELECT campaign_id, " +
            "COUNT(*) FILTER (WHERE event_type = 'CLICK') AS click_count, " +
            "COUNT(*) FILTER (WHERE event_type = 'IMPRESSION') AS impression_count " +
            "FROM ad_click_events WHERE timestamp >= :startTime " +
            "GROUP BY campaign_id ORDER BY click_count DESC LIMIT :lim")
    Flux<CampaignSummary> findTopCampaignsByEventCount(Instant startTime, int lim);
}
