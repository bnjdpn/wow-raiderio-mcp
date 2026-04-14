package dev.benjamin.wow.raiderio.tools;

import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.AffixesResponse;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import dev.benjamin.wow.raiderio.client.dto.MythicPlusRun;
import dev.benjamin.wow.raiderio.tools.format.TextFormatter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class WeeklyPlanTool {

    private static final String FIELDS =
        "mythic_plus_scores_by_season:current,mythic_plus_best_runs,mythic_plus_recent_runs";

    private final RaiderioClient client;

    public WeeklyPlanTool(RaiderioClient client) {
        this.client = client;
    }

    @Tool(
        name = "wow_weekly_plan",
        description = "Plan a WoW Mythic+ week for a character: current weekly affixes, priority dungeons with biggest score-gain potential, and Great Vault progression (1/4/8 runs)."
    )
    public String wow_weekly_plan(
            @ToolParam(description = "Character name") String name,
            @ToolParam(description = "Realm slug") String realm,
            @ToolParam(description = "Region: eu, us, kr, tw (default eu)") String region) {
        return wow_weekly_plan(name, realm, region, OffsetDateTime.now());
    }

    // Package-private overload for testability (inject "now").
    String wow_weekly_plan(String name, String realm, String region, OffsetDateTime now) {
        String reg = (region == null || region.isBlank()) ? "eu" : region.toLowerCase(Locale.ROOT);
        AffixesResponse affixes = client.fetchAffixes(reg, "en");
        CharacterProfile p = client.fetchCharacterProfile(reg, realm, name, FIELDS);

        TextFormatter f = new TextFormatter();
        renderAffixes(f, reg, affixes);
        renderPriorityDungeons(f, p);
        long runsThisWeek = renderVault(f, p, now);
        renderRecommendation(f, runsThisWeek);
        return f.toString();
    }

    private void renderAffixes(TextFormatter f, String region, AffixesResponse a) {
        f.section("Weekly affixes (" + region + ")");
        f.line(a.title());
        if (a.affixDetails() != null) {
            a.affixDetails().forEach(d -> f.bullet(d.name() + " — " + d.description()));
        }
    }

    private void renderPriorityDungeons(TextFormatter f, CharacterProfile p) {
        f.section("Priority dungeons (biggest score gain potential)");
        Map<String, Double> bestByDungeon = Optional.ofNullable(p.mythicPlusBestRuns()).orElse(List.of()).stream()
            .collect(Collectors.toMap(MythicPlusRun::dungeon, MythicPlusRun::score, Math::max));
        if (bestByDungeon.isEmpty()) {
            f.line("No best runs — start with any dungeon at a comfortable key level.");
            return;
        }
        bestByDungeon.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .limit(4)
            .forEach(e -> f.bullet(e.getKey() + " — current best score "
                + String.format(Locale.ROOT, "%.0f", e.getValue())));
    }

    private long renderVault(TextFormatter f, CharacterProfile p, OffsetDateTime now) {
        f.section("Great Vault progression");
        OffsetDateTime weekAgo = now.minusDays(7);
        long runsThisWeek = Optional.ofNullable(p.mythicPlusRecentRuns()).orElse(List.of()).stream()
            .filter(r -> r.completedAt() != null && r.completedAt().isAfter(weekAgo))
            .count();
        f.line("Runs completed this week: " + runsThisWeek);
        f.bullet("Slot 1 (1 run):  " + (runsThisWeek >= 1 ? "UNLOCKED" : "need " + (1 - runsThisWeek)));
        f.bullet("Slot 2 (4 runs): " + (runsThisWeek >= 4 ? "UNLOCKED" : "need " + (4 - runsThisWeek)));
        f.bullet("Slot 3 (8 runs): " + (runsThisWeek >= 8 ? "UNLOCKED" : "need " + (8 - runsThisWeek)));
        return runsThisWeek;
    }

    private void renderRecommendation(TextFormatter f, long runsThisWeek) {
        f.section("Recommendation");
        if (runsThisWeek < 8) {
            f.line("Focus on reaching 8 runs for full vault. Prioritize the dungeons above.");
        } else {
            f.line("Vault locked. Focus on pushing key levels in priority dungeons.");
        }
    }
}
