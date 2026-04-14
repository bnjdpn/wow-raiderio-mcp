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

class FindUpgradesToolTest {

    private final ObjectMapper mapper = JsonMapper.builder()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();

    private CharacterProfile fixture() throws Exception {
        return mapper.readValue(
            Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json")),
            CharacterProfile.class);
    }

    @Test
    void listsHeaderAndEquippedIlvl() throws Exception {
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixture());

        String out = new FindUpgradesTool(c).wow_find_upgrades("Stormrage", "hyjal", "eu");

        assertThat(out).contains("Stormrage — weakest gear slots");
        assertThat(out).contains("Equipped iLvl:");
    }

    @Test
    void listsAtMostFiveWeakestSlotsAsBullets() throws Exception {
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixture());

        String out = new FindUpgradesTool(c).wow_find_upgrades("Stormrage", "hyjal", "eu");

        long bullets = out.lines().filter(l -> l.startsWith("- ")).count();
        assertThat(bullets).isEqualTo(5);
    }

    @Test
    void weakestSlotIsLowestItemLevelFromFixture() throws Exception {
        // Fixture: wrist=632, feet=632 are lowest; trinket2/finger2/waist/back range 635-636.
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixture());

        String out = new FindUpgradesTool(c).wow_find_upgrades("Stormrage", "hyjal", "eu");

        assertThat(out).contains("wrist");
        assertThat(out).contains("feet");
        assertThat(out).contains("(ilvl 632)");
    }

    @Test
    void includesSourceHintPerSlot() throws Exception {
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixture());

        String out = new FindUpgradesTool(c).wow_find_upgrades("Stormrage", "hyjal", "eu");

        // wrist hint is "Crafted is often best in slot"
        assertThat(out).contains("Source: Crafted is often best in slot");
    }

    @Test
    void passesOnlyGearField() throws Exception {
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixture());

        new FindUpgradesTool(c).wow_find_upgrades("Stormrage", "hyjal", "eu");

        verify(c).fetchCharacterProfile(eq("eu"), eq("hyjal"), eq("Stormrage"), eq("gear"));
    }
}
