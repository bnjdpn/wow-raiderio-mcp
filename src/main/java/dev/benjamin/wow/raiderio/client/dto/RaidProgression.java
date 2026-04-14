package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RaidProgression(
    String summary,
    @JsonProperty("total_bosses") Integer totalBosses,
    @JsonProperty("normal_bosses_killed") Integer normalBossesKilled,
    @JsonProperty("heroic_bosses_killed") Integer heroicBossesKilled,
    @JsonProperty("mythic_bosses_killed") Integer mythicBossesKilled
) {}
