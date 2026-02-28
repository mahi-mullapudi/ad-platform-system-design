package com.tutorialq.systemdesign.adclick.service;

import com.tutorialq.systemdesign.adclick.domain.AdClickEvent;
import com.tutorialq.systemdesign.adclick.domain.CampaignSummary;
import com.tutorialq.systemdesign.adclick.repository.AdClickEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdClickEventService {

    private final AdClickEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Persist click event with idempotency via event_id unique constraint.
     * Returns the saved event or existing one if duplicate.
     */
    @Transactional
    public Mono<AdClickEvent> recordClickEvent(AdClickEvent event) {
        // Set timestamps
        Instant now = Instant.now();
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(now);
        }
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(now);
        }
        event.setUpdatedAt(now);

        return repository.save(event)
                .doOnSuccess(saved -> {
                    log.info("Persisted click event: {}", saved.getEventId());
                    // Publish to Kafka for stream processing
                    publishToKafka(saved);
                })
                .onErrorResume(e -> {
                    // Handle duplicate key violation (idempotent write)
                    log.warn("Duplicate event_id: {}, fetching existing record", event.getEventId());
                    return repository.findByEventId(event.getEventId());
                });
    }

    public Flux<AdClickEvent> getEventsByCampaignAndTimeRange(String campaignId, Instant start, Instant end) {
        return repository.findByCampaignIdAndTimeRange(campaignId, start, end);
    }

    public Flux<AdClickEvent> getEventsByAdAndTimeRange(String adId, Instant start, Instant end) {
        return repository.findByAdIdAndTimeRange(adId, start, end);
    }

    public Mono<Long> countEventsByCampaignAndType(String campaignId, String eventType, Instant startTime) {
        return repository.countByCampaignIdAndEventType(campaignId, eventType, startTime);
    }

    public Mono<Long> countEventsByCampaignTypeAndTimeRange(String campaignId, String eventType, Instant start, Instant end) {
        return repository.countByCampaignIdAndEventTypeInTimeRange(campaignId, eventType, start, end);
    }

    public Flux<CampaignSummary> getTopCampaigns(Instant start, int limit) {
        return repository.findTopCampaignsByEventCount(start, limit);
    }

    private void publishToKafka(AdClickEvent event) {
        try {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("eventId", event.getEventId().toString());
            dto.put("adId", event.getAdId());
            dto.put("campaignId", event.getCampaignId());
            dto.put("userId", event.getUserId());
            dto.put("eventType", event.getEventType());
            dto.put("timestamp", event.getTimestamp().toEpochMilli());
            dto.put("ipAddress", event.getIpAddress());
            dto.put("userAgent", event.getUserAgent());
            dto.put("metadata", event.getMetadata());
            kafkaTemplate.send("ad-click-events", event.getEventId().toString(), dto);
            log.debug("Published event to Kafka: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to publish event to Kafka: {}", event.getEventId(), e);
            // Don't fail the request if Kafka publish fails
        }
    }
}
