package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

public record MythicPlusRun(
    String dungeon,
    @JsonProperty("short_name") String shortName,
    @JsonProperty("mythic_level") int mythicLevel,
    @JsonProperty("num_keystone_upgrades") int numKeystoneUpgrades,
    double score,
    @JsonProperty("clear_time_ms") long clearTimeMs,
    @JsonProperty("par_time_ms") Long parTimeMs,
    @JsonProperty("completed_at") OffsetDateTime completedAt,
    List<Affix> affixes
) {
    public record Affix(String name) {}
    public boolean isTimed() { return numKeystoneUpgrades >= 1; }
    public boolean isDepleted() { return numKeystoneUpgrades == 0; }
}
