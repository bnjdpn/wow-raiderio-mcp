package dev.benjamin.wow.raiderio.tools;

import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import dev.benjamin.wow.raiderio.client.dto.MythicPlusLeaderboardResponse;
import dev.benjamin.wow.raiderio.tools.format.TextFormatter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class CompareToMetaTool {

    private final RaiderioClient client;
    private final String currentSeason;

    public CompareToMetaTool(RaiderioClient client,
                             @Value("${raiderio.current-season:season-tww-3}") String currentSeason) {
        this.client = client;
        this.currentSeason = currentSeason;
    }

    @Tool(
        name = "wow_compare_to_meta",
        description = "Compare a WoW character's spec and talents to the top Mythic+ leaderboard runs for the current season. Reports spec representation in the meta and the player's talent loadout string."
    )
    public String wow_compare_to_meta(
            @ToolParam(description = "Character name") String name,
            @ToolParam(description = "Realm slug") String realm,
            @ToolParam(description = "Region: eu, us, kr, tw (default eu)") String region) {

        String reg = (region == null || region.isBlank()) ? "eu" : region.toLowerCase(Locale.ROOT);
        CharacterProfile p = client.fetchCharacterProfile(reg, realm, name, "talents,gear");
        MythicPlusLeaderboardResponse lb = client.fetchLeaderboardRuns("world", currentSeason, "all", 0);

        TextFormatter f = new TextFormatter();
        f.section(p.name() + " vs meta");
        f.line("Spec: " + p.activeSpecName() + " " + p.characterClass());

        List<MythicPlusLeaderboardResponse.Ranking> rankings = Optional.ofNullable(lb.rankings()).orElse(List.of());
        long totalRosterSlots = rankings.stream()
            .flatMap(r -> Optional.ofNullable(r.run().roster()).orElse(List.<MythicPlusLeaderboardResponse.RosterMember>of()).stream())
            .count();
        long specAppearances = rankings.stream()
            .flatMap(r -> Optional.ofNullable(r.run().roster()).orElse(List.<MythicPlusLeaderboardResponse.RosterMember>of()).stream())
            .filter(m -> m.character() != null
                && p.characterClass().equalsIgnoreCase(m.character().characterClass())
                && p.activeSpecName().equalsIgnoreCase(m.character().spec()))
            .count();

        f.section("Meta presence");
        f.line(p.activeSpecName() + " " + p.characterClass() + " appears "
            + specAppearances + "/" + totalRosterSlots + " times in top leaderboard runs.");
        if (totalRosterSlots > 0) {
            f.line("Representation: " + TextFormatter.pct((double) specAppearances / totalRosterSlots));
        }

        f.section("Your talent loadout");
        if (p.talents() != null && p.talents().loadoutText() != null) {
            f.line(p.talents().loadoutText());
            f.line("(Import this into the in-game talent UI to inspect. Raider.io exposes the opaque loadout string.)");
        } else {
            f.line("No talent loadout returned.");
        }

        f.section("Note");
        f.line("Per-talent comparison requires decoding the loadout string (Wowhead / Icy Veins). This tool reports spec representation only.");

        return f.toString();
    }
}
