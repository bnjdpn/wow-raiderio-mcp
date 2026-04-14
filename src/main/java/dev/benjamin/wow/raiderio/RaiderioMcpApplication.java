package dev.benjamin.wow.raiderio;

import dev.benjamin.wow.raiderio.tools.CharacterAuditTool;
import dev.benjamin.wow.raiderio.tools.CompareToMetaTool;
import dev.benjamin.wow.raiderio.tools.EvaluatePlayerTool;
import dev.benjamin.wow.raiderio.tools.FindUpgradesTool;
import dev.benjamin.wow.raiderio.tools.WeeklyPlanTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableCaching
public class RaiderioMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaiderioMcpApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider wowTools(
            CharacterAuditTool auditTool,
            EvaluatePlayerTool evaluateTool,
            WeeklyPlanTool weeklyPlanTool,
            FindUpgradesTool findUpgradesTool,
            CompareToMetaTool compareToMetaTool) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(auditTool, evaluateTool, weeklyPlanTool, findUpgradesTool, compareToMetaTool)
            .build();
    }
}
