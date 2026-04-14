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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluatePlayerToolTest {

    private final ObjectMapper mapper = JsonMapper.builder()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();

    private CharacterProfile fixture() throws Exception {
        return mapper.readValue(
            Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json")),
            CharacterProfile.class);
    }

    @Test
    void rendersVerdictScoreConsistencyAndUtilities() throws Exception {
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixture());

        String out = new EvaluatePlayerTool(c).wow_evaluate_player("Stormrage", "hyjal", "eu", null);

        assertThat(out).contains("## Verdict");
        assertThat(out).contains("M+ Score");
        assertThat(out).contains("## Consistency");
        assertThat(out).contains("## Utilities");
        assertThat(out).containsIgnoringCase("Battle Shout");
    }

    @Test
    void rendersDungeonExperienceWhenDungeonProvided() throws Exception {
        RaiderioClient c = mock(RaiderioClient.class);
        CharacterProfile p = fixture();
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(p);

        String out = new EvaluatePlayerTool(c).wow_evaluate_player(
            "Stormrage", "hyjal", "eu", p.mythicPlusBestRuns().get(0).dungeon());
        assertThat(out).contains("## Dungeon experience:");
        assertThat(out).contains("Max timed key: +");
    }

    @Test
    void dungeonExperienceSectionAbsentWhenDungeonNull() throws Exception {
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixture());

        String out = new EvaluatePlayerTool(c).wow_evaluate_player("Stormrage", "hyjal", "eu", null);
        assertThat(out).doesNotContain("## Dungeon experience");
    }

    @Test
    void dungeonExperienceReportsNoTimedWhenAllDepletedForThatDungeon() throws Exception {
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixture());

        // Stonevault has num_keystone_upgrades: 0 in best runs fixture -> no timed runs on that dungeon
        String out = new EvaluatePlayerTool(c).wow_evaluate_player(
            "Stormrage", "hyjal", "eu", "The Stonevault");
        assertThat(out).contains("No timed runs on this dungeon.");
    }

    @Test
    void verdictIsStrongCandidateForFuryFixture() throws Exception {
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixture());

        String out = new EvaluatePlayerTool(c).wow_evaluate_player("Stormrage", "hyjal", "eu", null);
        // score = 2850.5 and ratio = 6/8 = 0.75 -> "Strong candidate for +13/+15 keys."
        assertThat(out).contains("Strong candidate for +13/+15 keys.");
    }
}
