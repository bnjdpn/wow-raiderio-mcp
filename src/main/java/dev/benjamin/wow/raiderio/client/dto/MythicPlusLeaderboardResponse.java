package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MythicPlusLeaderboardResponse(List<Ranking> rankings) {
    public record Ranking(Integer rank, Run run) {}

    public record Run(
        Dungeon dungeon,
        @JsonProperty("mythic_level") Integer mythicLevel,
        Double score,
        @JsonProperty("clear_time_ms") Long clearTimeMs,
        List<RosterMember> roster
    ) {}

    public record Dungeon(
        Integer id,
        String name,
        @JsonProperty("short_name") String shortName,
        String slug
    ) {}

    public record RosterMember(Character character) {}

    public record Character(
        String name,
        @JsonProperty("class") ClassInfo characterClass,
        SpecInfo spec,
        RaceInfo race
    ) {}

    public record ClassInfo(Integer id, String name, String slug) {}
    public record SpecInfo(Integer id, String name, String slug) {}
    public record RaceInfo(Integer id, String name, String slug, String faction) {}
}
