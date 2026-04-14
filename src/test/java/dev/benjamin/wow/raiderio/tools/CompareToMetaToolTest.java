package dev.benjamin.wow.raiderio.tools;

import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import dev.benjamin.wow.raiderio.client.dto.MythicPlusLeaderboardResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompareToMetaToolTest {

    private final ObjectMapper mapper = JsonMapper.builder()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();

    private CharacterProfile fixtureProfile() throws Exception {
        return mapper.readValue(
            Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json")),
            CharacterProfile.class);
    }

    private MythicPlusLeaderboardResponse leaderboardWithNFuryWarriors(int nFuryInRoster) {
        // Build 2 rankings with 5 roster members each (10 total). The first `nFuryInRoster` are Fury Warriors.
        List<MythicPlusLeaderboardResponse.RosterMember> r1 = List.of(
            member(n(nFuryInRoster, 0) ? "Fury Warrior" : "Spec1", n(nFuryInRoster, 0) ? "Warrior" : "Priest", n(nFuryInRoster, 0) ? "Fury" : "Holy"),
            member(n(nFuryInRoster, 1) ? "Fury Warrior" : "Spec2", n(nFuryInRoster, 1) ? "Warrior" : "Mage", n(nFuryInRoster, 1) ? "Fury" : "Frost"),
            member(n(nFuryInRoster, 2) ? "Fury Warrior" : "Spec3", n(nFuryInRoster, 2) ? "Warrior" : "Druid", n(nFuryInRoster, 2) ? "Fury" : "Guardian"),
            member(n(nFuryInRoster, 3) ? "Fury Warrior" : "Spec4", n(nFuryInRoster, 3) ? "Warrior" : "Hunter", n(nFuryInRoster, 3) ? "Fury" : "Beast Mastery"),
            member(n(nFuryInRoster, 4) ? "Fury Warrior" : "Spec5", n(nFuryInRoster, 4) ? "Warrior" : "Rogue", n(nFuryInRoster, 4) ? "Fury" : "Assassination")
        );
        List<MythicPlusLeaderboardResponse.RosterMember> r2 = List.of(
            member(n(nFuryInRoster, 5) ? "Fury Warrior" : "Spec6", n(nFuryInRoster, 5) ? "Warrior" : "Monk", n(nFuryInRoster, 5) ? "Fury" : "Windwalker"),
            member(n(nFuryInRoster, 6) ? "Fury Warrior" : "Spec7", n(nFuryInRoster, 6) ? "Warrior" : "Evoker", n(nFuryInRoster, 6) ? "Fury" : "Devastation"),
            member(n(nFuryInRoster, 7) ? "Fury Warrior" : "Spec8", n(nFuryInRoster, 7) ? "Warrior" : "Shaman", n(nFuryInRoster, 7) ? "Fury" : "Enhancement"),
            member(n(nFuryInRoster, 8) ? "Fury Warrior" : "Spec9", n(nFuryInRoster, 8) ? "Warrior" : "Paladin", n(nFuryInRoster, 8) ? "Fury" : "Retribution"),
            member(n(nFuryInRoster, 9) ? "Fury Warrior" : "SpecA", n(nFuryInRoster, 9) ? "Warrior" : "Death Knight", n(nFuryInRoster, 9) ? "Fury" : "Frost")
        );
        return new MythicPlusLeaderboardResponse(List.of(
            new MythicPlusLeaderboardResponse.Ranking(1, new MythicPlusLeaderboardResponse.Run(
                new MythicPlusLeaderboardResponse.Dungeon(1, "Ara-Kara", "ARAK", "arak"),
                20, 500.0, 1700000L, r1)),
            new MythicPlusLeaderboardResponse.Ranking(2, new MythicPlusLeaderboardResponse.Run(
                new MythicPlusLeaderboardResponse.Dungeon(2, "The Dawnbreaker", "DAWN", "dawn"),
                19, 490.0, 1750000L, r2))
        ));
    }

    private static boolean n(int countOfFury, int index) { return index < countOfFury; }

    private static MythicPlusLeaderboardResponse.RosterMember member(String playerName, String cls, String spec) {
        return new MythicPlusLeaderboardResponse.RosterMember(
            new MythicPlusLeaderboardResponse.Character(
                playerName,
                new MythicPlusLeaderboardResponse.ClassInfo(0, cls, cls.toLowerCase(Locale.ROOT).replace(' ', '-')),
                new MythicPlusLeaderboardResponse.SpecInfo(0, spec, spec.toLowerCase(Locale.ROOT).replace(' ', '-')),
                new MythicPlusLeaderboardResponse.RaceInfo(0, "Orc", "orc", "horde")));
    }

    @Test
    void rendersHeaderSpecAndMetaPresence() throws Exception {
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixtureProfile());
        when(c.fetchLeaderboardRuns(any(), any(), any(), anyInt()))
            .thenReturn(leaderboardWithNFuryWarriors(3));

        String out = new CompareToMetaTool(c, "season-tww-3").wow_compare_to_meta("Stormrage", "hyjal", "eu");

        assertThat(out).contains("Stormrage vs meta");
        assertThat(out).contains("Spec: Fury Warrior");
        assertThat(out).contains("## Meta presence");
        assertThat(out).contains("Fury Warrior appears 3/10 times");
        assertThat(out).contains("Representation: 30.0%");
    }

    @Test
    void rendersTalentLoadoutWithDisclaimer() throws Exception {
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixtureProfile());
        when(c.fetchLeaderboardRuns(any(), any(), any(), anyInt()))
            .thenReturn(leaderboardWithNFuryWarriors(0));

        String out = new CompareToMetaTool(c, "season-tww-3").wow_compare_to_meta("Stormrage", "hyjal", "eu");

        assertThat(out).contains("## Your talent loadout");
        assertThat(out).contains("CAUAAAAAAAAAAAAAAAAAAAAAAAAAAAAgMzMMzMYMwYDGmZGmxYYMzMzYGmxMmxyYMjZGmZmZ");
        assertThat(out).contains("Import this into the in-game talent UI");
    }

    @Test
    void rendersDisclaimerNoteAtEnd() throws Exception {
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixtureProfile());
        when(c.fetchLeaderboardRuns(any(), any(), any(), anyInt()))
            .thenReturn(leaderboardWithNFuryWarriors(0));

        String out = new CompareToMetaTool(c, "season-tww-3").wow_compare_to_meta("Stormrage", "hyjal", "eu");

        assertThat(out).contains("## Note");
        assertThat(out).contains("Per-talent comparison requires decoding");
    }

    @Test
    void handlesEmptyLeaderboardGracefully() throws Exception {
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(fixtureProfile());
        when(c.fetchLeaderboardRuns(any(), any(), any(), anyInt()))
            .thenReturn(new MythicPlusLeaderboardResponse(List.of()));

        String out = new CompareToMetaTool(c, "season-tww-3").wow_compare_to_meta("Stormrage", "hyjal", "eu");

        assertThat(out).contains("Fury Warrior appears 0/0 times");
        // Representation line should NOT appear when totalRosterSlots == 0
        assertThat(out).doesNotContain("Representation:");
    }
}
