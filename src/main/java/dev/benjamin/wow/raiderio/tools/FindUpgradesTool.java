package dev.benjamin.wow.raiderio.tools;

import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import dev.benjamin.wow.raiderio.client.dto.GearItem;
import dev.benjamin.wow.raiderio.tools.format.TextFormatter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

@Component
public class FindUpgradesTool {

    private static final Map<String, String> SOURCE_HINTS = Map.ofEntries(
        Map.entry("trinket1", "M+ dungeons (weekly vault) or raid"),
        Map.entry("trinket2", "M+ dungeons (weekly vault) or raid"),
        Map.entry("mainhand", "Raid drops or crafted embellished weapon"),
        Map.entry("offhand",  "Raid drops or crafted embellished weapon"),
        Map.entry("finger1",  "Raid or M+ dungeon — crafted rings are strong"),
        Map.entry("finger2",  "Raid or M+ dungeon — crafted rings are strong"),
        Map.entry("neck",     "Drops broadly in M+ / raid"),
        Map.entry("back",     "Drops in M+ / raid — or crafted"),
        Map.entry("chest",    "Tier set token — check vault / raid"),
        Map.entry("shoulder", "Tier set token — check vault / raid"),
        Map.entry("hands",    "Tier set token — check vault / raid"),
        Map.entry("head",     "Tier set token — check vault / raid"),
        Map.entry("legs",     "Tier set token — check vault / raid"),
        Map.entry("wrist",    "Crafted is often best in slot"),
        Map.entry("waist",    "Crafted is often best in slot"),
        Map.entry("feet",     "M+ dungeon drops")
    );

    private final RaiderioClient client;

    public FindUpgradesTool(RaiderioClient client) {
        this.client = client;
    }

    @Tool(
        name = "wow_find_upgrades",
        description = "Identify a WoW character's weakest gear slots (bottom 5 by item level) and suggest upgrade sources (M+, raid, craft)."
    )
    public String wow_find_upgrades(
            @ToolParam(description = "Character name") String name,
            @ToolParam(description = "Realm slug") String realm,
            @ToolParam(description = "Region: eu, us, kr, tw (default eu)") String region) {

        String reg = (region == null || region.isBlank()) ? "eu" : region.toLowerCase(Locale.ROOT);
        CharacterProfile p = client.fetchCharacterProfile(reg, realm, name, "gear");

        TextFormatter f = new TextFormatter();
        f.section(p.name() + " — weakest gear slots");
        if (p.gear() != null) {
            f.line("Equipped iLvl: " + String.format(Locale.ROOT, "%.1f", p.gear().itemLevelEquipped()));
        }

        f.section("Weakest slots (bottom 5 by item level)");
        if (p.gear() == null || p.gear().items() == null || p.gear().items().isEmpty()) {
            f.line("No gear data.");
            return f.toString();
        }
        p.gear().items().entrySet().stream()
            .filter(e -> e.getValue().itemLevel() != null)
            .sorted(Comparator.comparingInt(e -> e.getValue().itemLevel()))
            .limit(5)
            .forEach(e -> {
                GearItem it = e.getValue();
                String hint = SOURCE_HINTS.getOrDefault(e.getKey(), "M+ / raid");
                f.bullet(e.getKey() + " — " + it.name() + " (ilvl " + it.itemLevel() + "). Source: " + hint);
            });
        return f.toString();
    }
}
