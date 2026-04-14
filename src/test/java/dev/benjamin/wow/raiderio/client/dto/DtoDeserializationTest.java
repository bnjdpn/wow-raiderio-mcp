package dev.benjamin.wow.raiderio.client.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DtoDeserializationTest {
    private final ObjectMapper mapper = JsonMapper.builder()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();

    @Test
    void deserializesFullCharacterProfile() throws Exception {
        String json = Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json"));
        CharacterProfile profile = mapper.readValue(json, CharacterProfile.class);

        assertThat(profile.name()).isEqualTo("Stormrage");
        assertThat(profile.characterClass()).isEqualTo("Warrior");
        assertThat(profile.activeSpecName()).isEqualTo("Fury");
        assertThat(profile.gear().itemLevelEquipped()).isGreaterThan(600);
        assertThat(profile.gear().items()).isNotEmpty();
        assertThat(profile.gear().items().get("neck").enchant()).isEqualTo(0);
        assertThat(profile.mythicPlusBestRuns()).isNotEmpty();
        assertThat(profile.mythicPlusRecentRuns()).isNotEmpty();
        assertThat(profile.mythicPlusScoresBySeason().get(0).scores().get("all")).isEqualTo(2850.5);
        assertThat(profile.mythicPlusRecentRuns().stream().anyMatch(r -> !r.isTimed())).isTrue();
        assertThat(profile.raidProgression()).containsKey("nerub-ar-palace");
    }

    @Test
    void deserializesAffixes() throws Exception {
        String json = Files.readString(Path.of("src/test/resources/fixtures/affixes-week.json"));
        AffixesResponse a = mapper.readValue(json, AffixesResponse.class);
        assertThat(a.title()).contains("Fortified");
        assertThat(a.affixDetails()).hasSize(3);
    }

    @Test
    void deserializesLeaderboardRuns() throws Exception {
        String json = Files.readString(Path.of("src/test/resources/fixtures/leaderboard-runs.json"));
        MythicPlusLeaderboardResponse r = mapper.readValue(json, MythicPlusLeaderboardResponse.class);
        assertThat(r.rankings()).hasSize(1);
        assertThat(r.rankings().get(0).run().dungeon().shortName()).isEqualTo("ARAK");
        assertThat(r.rankings().get(0).run().roster()).hasSize(5);

        var firstChar = r.rankings().get(0).run().roster().get(0).character();
        assertThat(firstChar.name()).isEqualTo("Topwar");
        assertThat(firstChar.characterClass().name()).isEqualTo("Warrior");
        assertThat(firstChar.spec().name()).isEqualTo("Fury");
        assertThat(firstChar.race().name()).isEqualTo("Orc");
        assertThat(firstChar.race().faction()).isEqualTo("horde");
    }
}
