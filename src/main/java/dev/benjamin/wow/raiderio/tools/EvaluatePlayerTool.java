package dev.benjamin.wow.raiderio.tools;

import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import dev.benjamin.wow.raiderio.client.dto.MythicPlusRun;
import dev.benjamin.wow.raiderio.tools.format.TextFormatter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class EvaluatePlayerTool {

    private static final Map<String, List<String>> CLASS_UTILITIES = Map.ofEntries(
        Map.entry("Warrior",      List.of("Battle Shout (+5% attack power)", "Rallying Cry (raid CD)", "Pummel (interrupt)", "Shattering Throw (immunity removal)")),
        Map.entry("Paladin",      List.of("Blessing of the Bronze/Summer/etc.", "Hand of Freedom", "Battle Rez (Holy: NO; Ret/Prot: Lay on Hands)", "Rebuke (interrupt)")),
        Map.entry("Hunter",       List.of("Tranq Shot (enrage/magic dispel)", "Misdirection", "Counter Shot (interrupt)")),
        Map.entry("Rogue",        List.of("Shroud of Concealment (skip)", "Kick (interrupt)", "Tricks of the Trade", "Smoke Bomb")),
        Map.entry("Priest",       List.of("Power Word: Fortitude (raid buff)", "Mass Dispel", "Mind Soothe", "Silence (Shadow: interrupt)")),
        Map.entry("Death Knight", List.of("Anti-Magic Zone", "Mind Freeze (interrupt)", "Death Grip", "Battle Rez via Raise Ally")),
        Map.entry("Shaman",       List.of("Bloodlust/Heroism", "Purge", "Wind Shear (interrupt)", "Spirit Link Totem (Resto)")),
        Map.entry("Mage",         List.of("Time Warp (Bloodlust)", "Arcane Intellect", "Counterspell (interrupt)", "Spellsteal")),
        Map.entry("Warlock",      List.of("Summon", "Battle Rez via Soulstone", "Spell Lock (pet interrupt)", "Curse dispel")),
        Map.entry("Monk",         List.of("Mystic Touch (+5% physical damage taken by target)", "Spear Hand Strike (interrupt)", "Revival (Mistweaver)", "Ring of Peace")),
        Map.entry("Druid",        List.of("Mark of the Wild", "Soothe (enrage dispel)", "Skull Bash/Solar Beam (interrupt)", "Battle Rez (Rebirth)")),
        Map.entry("Demon Hunter", List.of("Chaos Brand (+5% magic damage taken)", "Disrupt (interrupt)", "Darkness (raid CD)", "Imprison")),
        Map.entry("Evoker",       List.of("Blessing of the Bronze", "Time Dilation", "Quell (interrupt)", "Rescue"))
    );

    private static final String FIELDS =
        "mythic_plus_scores_by_season:current,mythic_plus_best_runs,mythic_plus_recent_runs,gear";

    private final RaiderioClient client;

    public EvaluatePlayerTool(RaiderioClient client) {
        this.client = client;
    }

    @Tool(
        name = "wow_evaluate_player",
        description = "Evaluate a WoW player as a potential Mythic+ group candidate. Returns score, consistency (timed ratio), optional per-dungeon experience, class utilities, and a one-line verdict."
    )
    public String wow_evaluate_player(
            @ToolParam(description = "Character name") String name,
            @ToolParam(description = "Realm slug") String realm,
            @ToolParam(description = "Region: eu, us, kr, tw (default eu)") String region,
            @ToolParam(description = "Optional dungeon name or short code to report player's experience on that dungeon", required = false) String dungeon) {

        String reg = (region == null || region.isBlank()) ? "eu" : region.toLowerCase(Locale.ROOT);
        CharacterProfile p = client.fetchCharacterProfile(reg, realm, name, FIELDS);

        TextFormatter f = new TextFormatter();
        f.section(p.name() + " — " + p.activeSpecName() + " " + p.characterClass());

        double score = 0.0;
        if (p.mythicPlusScoresBySeason() != null && !p.mythicPlusScoresBySeason().isEmpty()) {
            score = p.mythicPlusScoresBySeason().get(0).scores().getOrDefault("all", 0.0);
        }
        f.line("M+ Score: " + String.format(Locale.ROOT, "%.0f", score));
        if (p.gear() != null) {
            f.line("Equipped iLvl: " + String.format(Locale.ROOT, "%.1f", p.gear().itemLevelEquipped()));
        }

        f.section("Consistency");
        List<MythicPlusRun> recent = Optional.ofNullable(p.mythicPlusRecentRuns()).orElse(List.of());
        double ratio = 0.0;
        if (recent.isEmpty()) {
            f.line("No recent runs on record.");
        } else {
            long timed = recent.stream().filter(MythicPlusRun::isTimed).count();
            ratio = (double) timed / recent.size();
            f.line(timed + "/" + recent.size() + " timed (" + TextFormatter.pct(ratio) + ")");
        }

        if (dungeon != null && !dungeon.isBlank()) {
            f.section("Dungeon experience: " + dungeon);
            int maxTimed = Optional.ofNullable(p.mythicPlusBestRuns()).orElse(List.of()).stream()
                .filter(r -> matchesDungeon(r, dungeon))
                .filter(MythicPlusRun::isTimed)
                .mapToInt(MythicPlusRun::mythicLevel)
                .max()
                .orElse(-1);
            f.line(maxTimed < 0 ? "No timed runs on this dungeon." : "Max timed key: +" + maxTimed);
        }

        f.section("Utilities");
        List<String> utils = CLASS_UTILITIES.getOrDefault(p.characterClass(), List.of("Unknown class"));
        utils.forEach(f::bullet);

        f.section("Verdict");
        f.line(verdict(score, ratio));

        return f.toString();
    }

    private boolean matchesDungeon(MythicPlusRun r, String query) {
        String q = query.toLowerCase(Locale.ROOT);
        if (r.dungeon() != null && r.dungeon().toLowerCase(Locale.ROOT).contains(q)) return true;
        return r.shortName() != null && r.shortName().equalsIgnoreCase(query);
    }

    private String verdict(double score, double ratio) {
        if (score >= 3000 && ratio >= 0.75) return "Top-tier candidate — take without hesitation.";
        if (score >= 2500 && ratio >= 0.6)  return "Strong candidate for +13/+15 keys.";
        if (score >= 2000)                  return "Solid for +10 to +12 range.";
        if (score >= 1500)                  return "Entry-level; suitable for +6 to +9.";
        return "Low score — progression player, expect to carry.";
    }
}
