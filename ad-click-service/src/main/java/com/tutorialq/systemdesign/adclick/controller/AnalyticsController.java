package com.tutorialq.systemdesign.adclick.controller;

import com.tutorialq.systemdesign.adclick.domain.CampaignSummary;
import com.tutorialq.systemdesign.adclick.service.AdClickEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AdClickEventService service;

    @GetMapping("/campaign/{campaignId}/summary")
    public Mono<Map<String, Object>> getCampaignSummary(
            @PathVariable String campaignId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {

        Mono<Long> clicks = service.countEventsByCampaignTypeAndTimeRange(campaignId, "CLICK", start, end);
        Mono<Long> impressions = service.countEventsByCampaignTypeAndTimeRange(campaignId, "IMPRESSION", start, end);

        return Mono.zip(clicks, impressions, (clickCount, impressionCount) -> {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("campaignId", campaignId);
            summary.put("clickCount", clickCount);
            summary.put("impressionCount", impressionCount);
            summary.put("start", start.toString());
            summary.put("end", end.toString());
            return summary;
        });
    }

    @GetMapping("/campaigns/top")
    public Flux<CampaignSummary> getTopCampaigns(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(defaultValue = "10") int limit) {
        return service.getTopCampaigns(start, limit);
    }
}
