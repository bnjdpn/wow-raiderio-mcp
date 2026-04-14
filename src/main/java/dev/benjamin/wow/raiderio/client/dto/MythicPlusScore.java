package dev.benjamin.wow.raiderio.client.dto;

import java.util.Map;

public record MythicPlusScore(
    String season,
    Map<String, Double> scores,
    Map<String, Object> segments
) {}
