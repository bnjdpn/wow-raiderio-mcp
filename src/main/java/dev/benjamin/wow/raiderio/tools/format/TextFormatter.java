package dev.benjamin.wow.raiderio.tools.format;

import java.util.Locale;

public class TextFormatter {

    private final StringBuilder sb = new StringBuilder();

    public TextFormatter section(String title) {
        if (!sb.isEmpty()) {
            sb.append('\n');
        }
        sb.append("## ").append(title).append('\n');
        return this;
    }

    public TextFormatter line(String text) {
        sb.append(text).append('\n');
        return this;
    }

    public TextFormatter bullet(String text) {
        sb.append("- ").append(text).append('\n');
        return this;
    }

    public TextFormatter blank() {
        sb.append('\n');
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    public static String pct(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0);
    }
}
