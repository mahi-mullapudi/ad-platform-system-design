package com.tutorialq.systemdesign.adclick.controller;

import com.tutorialq.systemdesign.adclick.domain.AdClickEvent;
import com.tutorialq.systemdesign.adclick.service.AdClickEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class AdClickEventController {

    private final AdClickEventService service;

    @PostMapping("/clicks")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AdClickEvent> recordClick(@Valid @RequestBody AdClickEvent event) {
        event.setEventType("CLICK");
        return service.recordClickEvent(event);
    }

    @PostMapping("/impressions")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AdClickEvent> recordImpression(@Valid @RequestBody AdClickEvent event) {
        event.setEventType("IMPRESSION");
        return service.recordClickEvent(event);
    }

    @GetMapping("/campaign/{campaignId}")
    public Flux<AdClickEvent> getEventsByCampaign(
            @PathVariable String campaignId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return service.getEventsByCampaignAndTimeRange(campaignId, start, end);
    }

    @GetMapping("/ad/{adId}")
    public Flux<AdClickEvent> getEventsByAd(
            @PathVariable String adId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return service.getEventsByAdAndTimeRange(adId, start, end);
    }

    @GetMapping("/campaign/{campaignId}/count")
    public Mono<Long> countEvents(
            @PathVariable String campaignId,
            @RequestParam String eventType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime) {
        return service.countEventsByCampaignAndType(campaignId, eventType, startTime);
    }
}
