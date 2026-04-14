package dev.benjamin.wow.raiderio.tools;

import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterAuditToolTest {

    private final ObjectMapper mapper = JsonMapper.builder()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();

    private CharacterProfile fixture() throws Exception {
        return mapper.readValue(
            Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json")),
            CharacterProfile.class);
    }

    @Test
    void rendersHeaderScoreGearAndTimedRatio() throws Exception {
        RaiderioClient client = mock(RaiderioClient.class);
        when(client.fetchCharacterProfile(eq("eu"), eq("hyjal"), eq("Stormrage"), any()))
            .thenReturn(fixture());

        String out = new CharacterAuditTool(client).wow_character_audit("Stormrage", "hyjal", "eu");

        assertThat(out).contains("Stormrage");
        assertThat(out).contains("Fury Warrior");
        assertThat(out).contains("M+ Score");
        assertThat(out).contains("Equipped iLvl");
        assertThat(out).contains("Raid progression");
        assertThat(out).contains("## Missing enchants");
        assertThat(out).contains("## Missing gems");
        assertThat(out).contains("## Best runs");
        assertThat(out).contains("## Weakest dungeon");
        assertThat(out).contains("## Recent timed ratio");
    }

    @Test
    void detectsMissingEnchantOnNeckAndOffhand() throws Exception {
        RaiderioClient client = mock(RaiderioClient.class);
        when(client.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixture());

        String out = new CharacterAuditTool(client).wow_character_audit("Stormrage", "hyjal", "eu");

        // Fixture has offhand with enchant=0 (and finger2, back, legs, feet, wrist, chest is enchanted)
        assertThat(out).contains("- offhand");
        assertThat(out).contains("- finger2");
    }

    @Test
    void reportsTimedRatioFromRecentRuns() throws Exception {
        RaiderioClient client = mock(RaiderioClient.class);
        when(client.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixture());

        String out = new CharacterAuditTool(client).wow_character_audit("Stormrage", "hyjal", "eu");

        // Fixture has 8 recent runs, 2 depleted -> 6/8 timed = 75.0%
        assertThat(out).contains("6/8 timed");
        assertThat(out).contains("75.0%");
    }

    @Test
    void passesCorrectFieldsParameterToClient() throws Exception {
        RaiderioClient client = mock(RaiderioClient.class);
        when(client.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixture());

        new CharacterAuditTool(client).wow_character_audit("Stormrage", "hyjal", "eu");

        verify(client).fetchCharacterProfile(eq("eu"), eq("hyjal"), eq("Stormrage"),
            org.mockito.ArgumentMatchers.contains("mythic_plus_scores_by_season:current"));
    }

    @Test
    void defaultsRegionToEuWhenBlank() throws Exception {
        RaiderioClient client = mock(RaiderioClient.class);
        when(client.fetchCharacterProfile(eq("eu"), any(), any(), any())).thenReturn(fixture());

        String out = new CharacterAuditTool(client).wow_character_audit("Stormrage", "hyjal", "");
        assertThat(out).isNotBlank();
        verify(client).fetchCharacterProfile(eq("eu"), any(), any(), any());
    }
}
