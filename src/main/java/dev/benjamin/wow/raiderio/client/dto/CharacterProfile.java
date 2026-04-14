package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record CharacterProfile(
    String name,
    String race,
    @JsonProperty("class") String characterClass,
    @JsonProperty("active_spec_name") String activeSpecName,
    @JsonProperty("active_spec_role") String activeSpecRole,
    String region,
    String realm,
    String faction,
    @JsonProperty("thumbnail_url") String thumbnailUrl,
    @JsonProperty("mythic_plus_scores_by_season") List<MythicPlusScore> mythicPlusScoresBySeason,
    @JsonProperty("mythic_plus_best_runs") List<MythicPlusRun> mythicPlusBestRuns,
    @JsonProperty("mythic_plus_recent_runs") List<MythicPlusRun> mythicPlusRecentRuns,
    @JsonProperty("mythic_plus_alternate_runs") List<MythicPlusRun> mythicPlusAlternateRuns,
    GearSummary gear,
    TalentLoadout talents,
    @JsonProperty("raid_progression") Map<String, RaidProgression> raidProgression
) {}
