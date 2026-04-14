package dev.benjamin.wow.raiderio.tools.format;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TextFormatterTest {

    @Test
    void sectionRendersHeaderAndBullets() {
        String out = new TextFormatter()
            .section("Summary")
            .line("Score: 2850")
            .line("Rank: Hero")
            .bullet("Strength: stuff")
            .bullet("Weakness: enchants")
            .toString();

        assertThat(out).contains("## Summary");
        assertThat(out).contains("Score: 2850");
        assertThat(out).contains("- Strength: stuff");
        assertThat(out).contains("- Weakness: enchants");
    }

    @Test
    void multipleSectionsAreSeparatedByBlankLine() {
        String out = new TextFormatter()
            .section("First")
            .line("a")
            .section("Second")
            .line("b")
            .toString();

        assertThat(out).contains("a\n\n## Second");
    }

    @Test
    void firstSectionHasNoLeadingBlank() {
        String out = new TextFormatter().section("Top").toString();
        assertThat(out).startsWith("## Top");
    }

    @Test
    void blankInsertsEmptyLine() {
        String out = new TextFormatter().line("a").blank().line("b").toString();
        assertThat(out).isEqualTo("a\n\nb\n");
    }

    @Test
    void pctFormatsWithOneDecimal() {
        assertThat(TextFormatter.pct(0.8333)).isEqualTo("83.3%");
        assertThat(TextFormatter.pct(1.0)).isEqualTo("100.0%");
        assertThat(TextFormatter.pct(0.0)).isEqualTo("0.0%");
        assertThat(TextFormatter.pct(0.666666)).isEqualTo("66.7%");
    }
}
