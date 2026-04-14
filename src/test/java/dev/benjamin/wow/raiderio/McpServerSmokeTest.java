package dev.benjamin.wow.raiderio;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = { "spring.main.web-application-type=none" })
class McpServerSmokeTest {

    @Autowired
    ToolCallbackProvider provider;

    @Test
    void allFiveToolsAreRegistered() {
        List<String> names = Arrays.stream(provider.getToolCallbacks())
            .map(cb -> cb.getToolDefinition().name())
            .toList();

        assertThat(names).containsExactlyInAnyOrder(
            "wow_character_audit",
            "wow_evaluate_player",
            "wow_weekly_plan",
            "wow_find_upgrades",
            "wow_compare_to_meta");
    }

    @Test
    void toolDescriptionsAreNonEmpty() {
        Arrays.stream(provider.getToolCallbacks())
            .forEach(cb -> assertThat(cb.getToolDefinition().description())
                .as("description of %s", cb.getToolDefinition().name())
                .isNotBlank());
    }
}
