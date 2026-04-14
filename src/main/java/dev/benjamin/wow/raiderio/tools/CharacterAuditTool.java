package dev.benjamin.wow.raiderio.tools;

import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import dev.benjamin.wow.raiderio.client.dto.GearItem;
import dev.benjamin.wow.raiderio.client.dto.MythicPlusRun;
import dev.benjamin.wow.raiderio.tools.format.TextFormatter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
public class CharacterAuditTool {

    private static final Set<String> ENCHANT_SLOTS = Set.of(
        "mainhand", "offhand", "finger1", "finger2", "back", "legs", "feet", "wrist", "chest");

    private static final Set<String> SOCKET_SLOTS = Set.of(
        "head", "neck", "wrist", "waist", "finger1", "finger2");

    private static final String FIELDS =
        "mythic_plus_scores_by_season:current,mythic_plus_best_runs,mythic_plus_recent_runs,gear,talents,raid_progression";

    private final RaiderioClient client;

    public CharacterAuditTool(RaiderioClient client) {
        this.client = client;
    }

    @Tool(
        name = "wow_character_audit",
        description = "Audit complet d'un personnage World of Warcraft : score Mythic+, stuff, enchants, gems, talents, runs recents et meilleurs runs. Identifie les faiblesses et les axes d'amelioration."
    )
    public String wow_character_audit(
            @ToolParam(description = "Character name (case-insensitive)") String name,
            @ToolParam(description = "Realm slug (lowercase, dashes for spaces, e.g. 'hyjal' or 'argent-dawn')") String realm,
            @ToolParam(description = "Region: eu, us, kr, tw (default eu)") String region) {

        String reg = (region == null || region.isBlank()) ? "eu" : region.toLowerCase(Locale.ROOT);
        CharacterProfile p = client.fetchCharacterProfile(reg, realm, name, FIELDS);

        TextFormatter f = new TextFormatter();

        f.section(p.name() + " — " + p.activeSpecName() + " " + p.characterClass()
            + " (" + reg + "/" + p.realm() + ")");

        double score = 0.0;
        if (p.mythicPlusScoresBySeason() != null && !p.mythicPlusScoresBySeason().isEmpty()) {
            score = p.mythicPlusScoresBySeason().get(0).scores().getOrDefault("all", 0.0);
        }
        f.line("M+ Score (current season): " + String.format(Locale.ROOT, "%.0f", score));

        if (p.gear() != null) {
            f.line("Equipped iLvl: " + String.format(Locale.ROOT, "%.1f", p.gear().itemLevelEquipped()));
        }
        if (p.raidProgression() != null && !p.raidProgression().isEmpty()) {
            var tier = p.raidProgression().values().iterator().next();
            f.line("Raid progression: " + tier.summary());
        }

        renderMissingEnchants(f, p);
        renderMissingGems(f, p);
        renderBestRuns(f, p);
        renderWeakestDungeon(f, p);
        renderTimedRatio(f, p);

        return f.toString();
    }

    private void renderMissingEnchants(TextFormatter f, CharacterProfile p) {
        f.section("Missing enchants");
        List<String> missing = new ArrayList<>();
        if (p.gear() != null && p.gear().items() != null) {
            for (var e : p.gear().items().entrySet()) {
                if (ENCHANT_SLOTS.contains(e.getKey())
                        && (e.getValue().enchant() == null || e.getValue().enchant() == 0)) {
                    missing.add(e.getKey() + " (" + e.getValue().name() + ")");
                }
            }
        }
        if (missing.isEmpty()) {
            f.line("None — all enchantable slots are enchanted.");
        } else {
            missing.forEach(f::bullet);
        }
    }

    private void renderMissingGems(TextFormatter f, CharacterProfile p) {
        f.section("Missing gems (heuristic)");
        List<String> noGem = new ArrayList<>();
        if (p.gear() != null && p.gear().items() != null) {
            for (var e : p.gear().items().entrySet()) {
                GearItem item = e.getValue();
                if (SOCKET_SLOTS.contains(e.getKey())
                        && (item.gems() == null || item.gems().isEmpty())) {
                    noGem.add(e.getKey() + " (" + item.name() + ")");
                }
            }
        }
        if (noGem.isEmpty()) {
            f.line("None detected.");
        } else {
            noGem.forEach(f::bullet);
        }
    }

    private void renderBestRuns(TextFormatter f, CharacterProfile p) {
        f.section("Best runs");
        List<MythicPlusRun> best = Optional.ofNullable(p.mythicPlusBestRuns()).orElse(List.of());
        if (best.isEmpty()) {
            f.line("No best runs recorded.");
            return;
        }
        best.stream()
            .sorted(Comparator.comparingDouble(MythicPlusRun::score).reversed())
            .limit(5)
            .forEach(r -> f.bullet(
                r.dungeon() + " +" + r.mythicLevel()
                    + " (" + (r.isTimed() ? "timed" : "depleted")
                    + ", score " + String.format(Locale.ROOT, "%.0f", r.score()) + ")"));
    }

    private void renderWeakestDungeon(TextFormatter f, CharacterProfile p) {
        List<MythicPlusRun> best = Optional.ofNullable(p.mythicPlusBestRuns()).orElse(List.of());
        MythicPlusRun worst = best.stream()
            .min(Comparator.comparingDouble(MythicPlusRun::score))
            .orElse(null);
        if (worst == null) return;
        f.section("Weakest dungeon");
        f.line(worst.dungeon() + " +" + worst.mythicLevel()
            + " — score " + String.format(Locale.ROOT, "%.0f", worst.score())
            + ". Prioritize this for the biggest score gain.");
    }

    private void renderTimedRatio(TextFormatter f, CharacterProfile p) {
        f.section("Recent timed ratio");
        List<MythicPlusRun> recent = Optional.ofNullable(p.mythicPlusRecentRuns()).orElse(List.of());
        if (recent.isEmpty()) {
            f.line("No recent runs.");
            return;
        }
        long timed = recent.stream().filter(MythicPlusRun::isTimed).count();
        f.line(timed + "/" + recent.size() + " timed (" + TextFormatter.pct((double) timed / recent.size()) + ")");
    }
}
