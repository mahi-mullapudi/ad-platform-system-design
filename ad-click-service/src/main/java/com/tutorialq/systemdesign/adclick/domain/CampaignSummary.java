package com.tutorialq.systemdesign.adclick.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampaignSummary {
    private String campaignId;
    private long clickCount;
    private long impressionCount;
}
