package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GearItem(
    @JsonProperty("item_id") Long itemId,
    @JsonProperty("item_level") Integer itemLevel,
    String name,
    Integer enchant,
    List<Long> gems,
    String tier
) {}
