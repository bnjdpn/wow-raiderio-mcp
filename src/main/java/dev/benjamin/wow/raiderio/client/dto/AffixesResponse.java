package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AffixesResponse(
    String region,
    String title,
    @JsonProperty("leaderboard_url") String leaderboardUrl,
    @JsonProperty("affix_details") List<AffixDetail> affixDetails
) {
    public record AffixDetail(Integer id, String name, String description) {}
}
