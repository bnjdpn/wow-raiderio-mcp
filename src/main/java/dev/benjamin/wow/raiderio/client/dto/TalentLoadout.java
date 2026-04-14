package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TalentLoadout(
    @JsonProperty("loadout_text") String loadoutText,
    @JsonProperty("loadout_spec_id") Integer loadoutSpecId,
    @JsonProperty("class_id") Integer classId
) {}
