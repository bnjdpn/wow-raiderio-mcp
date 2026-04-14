package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record GearSummary(
    @JsonProperty("item_level_equipped") double itemLevelEquipped,
    @JsonProperty("item_level_total") Double itemLevelTotal,
    Map<String, GearItem> items
) {}
