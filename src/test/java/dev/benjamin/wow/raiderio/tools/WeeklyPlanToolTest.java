package dev.benjamin.wow.raiderio.tools;

import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.AffixesResponse;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeeklyPlanToolTest {

    private final ObjectMapper mapper = JsonMapper.builder()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();

    private CharacterProfile fixtureProfile() throws Exception {
        return mapper.readValue(
            Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json")),
            CharacterProfile.class);
    }

    private AffixesResponse sampleAffixes() {
        return new AffixesResponse("eu", "Fortified, Bolstering, Raging", "https://raider.io/x",
            List.of(
                new AffixesResponse.AffixDetail(10, "Fortified", "Non-boss enemies have 20% more health."),
                new AffixesResponse.AffixDetail(7, "Bolstering", "Dying mobs empower nearby allies."),
                new AffixesResponse.AffixDetail(6, "Raging", "Enemies enrage at 30% health.")
            ));
    }

    @Test
    void rendersAffixesSectionWithBullets() throws Exception {
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixtureProfile());
        when(c.fetchAffixes(any(), any())).thenReturn(sampleAffixes());

        String out = new WeeklyPlanTool(c).wow_weekly_plan("Stormrage", "hyjal", "eu",
            OffsetDateTime.of(2026, 4, 14, 10, 0, 0, 0, ZoneOffset.UTC));

        assertThat(out).contains("## Weekly affixes (eu)");
        assertThat(out).contains("Fortified, Bolstering, Raging");
        assertThat(out).contains("- Fortified —");
        assertThat(out).contains("- Bolstering —");
    }

    @Test
    void rendersPriorityDungeonsSortedByLowestScore() throws Exception {
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixtureProfile());
        when(c.fetchAffixes(any(), any())).thenReturn(sampleAffixes());

        String out = new WeeklyPlanTool(c).wow_weekly_plan("Stormrage", "hyjal", "eu",
            OffsetDateTime.of(2026, 4, 14, 10, 0, 0, 0, ZoneOffset.UTC));

        assertThat(out).contains("## Priority dungeons");
        // Fixture's lowest score is Grim Batol at 140 (best runs)
        assertThat(out).contains("Grim Batol");
    }

    @Test
    void countsRunsFromLastSevenDays() throws Exception {
        // The fixture's recent runs span 2026-04-05 to 2026-04-12. With now=2026-04-14, all 8 fall in the last 7 days -> vault slot 3 UNLOCKED.
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixtureProfile());
        when(c.fetchAffixes(any(), any())).thenReturn(sampleAffixes());

        String out = new WeeklyPlanTool(c).wow_weekly_plan("Stormrage", "hyjal", "eu",
            OffsetDateTime.of(2026, 4, 14, 10, 0, 0, 0, ZoneOffset.UTC));

        assertThat(out).contains("Runs completed this week: 8");
        assertThat(out).contains("Slot 1 (1 run):  UNLOCKED");
        assertThat(out).contains("Slot 2 (4 runs): UNLOCKED");
        assertThat(out).contains("Slot 3 (8 runs): UNLOCKED");
        assertThat(out).contains("Vault locked. Focus on pushing key levels");
    }

    @Test
    void reportsDeficitWhenFewerRunsThisWeek() throws Exception {
        // Push "now" far forward so only 0 runs are within the last 7 days.
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixtureProfile());
        when(c.fetchAffixes(any(), any())).thenReturn(sampleAffixes());

        String out = new WeeklyPlanTool(c).wow_weekly_plan("Stormrage", "hyjal", "eu",
            OffsetDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));

        assertThat(out).contains("Runs completed this week: 0");
        assertThat(out).contains("need 1");
        assertThat(out).contains("need 4");
        assertThat(out).contains("need 8");
        assertThat(out).contains("Focus on reaching 8 runs for full vault.");
    }
}
