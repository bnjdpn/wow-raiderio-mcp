# WoW Raider.io MCP Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Boot MCP server (STDIO transport) exposing 5 intent-based WoW gameplay tools backed by the Raider.io public API.

**Architecture:** Spring Boot 3.4 + Spring AI MCP Server 1.0.3. A `RaiderioClient` wraps WebClient with Caffeine caching. Each of the 5 tools is a `@Component` with a `@Tool`-annotated method; tools aggregate multiple API calls into a human-readable text summary. A `MethodToolCallbackProvider` bean registers all tools. Tests use WireMock to mock the Raider.io API.

**Tech Stack:** Java 21, Spring Boot 3.4.x, spring-ai-starter-mcp-server 1.0.3, WebFlux WebClient, Caffeine, Maven, JUnit 5, WireMock, AssertJ.

---

## File Structure

```
wow-raiderio-mcp/
├── pom.xml
├── README.md
├── src/main/java/dev/benjamin/wow/raiderio/
│   ├── RaiderioMcpApplication.java           # Spring Boot entry + ToolCallbackProvider bean
│   ├── config/
│   │   ├── RaiderioConfig.java               # WebClient bean + @ConfigurationProperties
│   │   └── CacheConfig.java                  # Caffeine CacheManager (3 named caches)
│   ├── client/
│   │   ├── RaiderioClient.java               # HTTP client, cacheable methods
│   │   ├── RaiderioException.java            # Runtime exception for API failures
│   │   └── dto/
│   │       ├── CharacterProfile.java         # record: name, class, spec, gear, talents, scores, runs, raid_progression
│   │       ├── GearItem.java                 # record: slot, item_level, name, enchant, gems
│   │       ├── GearSummary.java              # record: item_level_equipped, items Map<String, GearItem>
│   │       ├── MythicPlusRun.java            # record: dungeon, short_name, mythic_level, num_keystone_upgrades, score, clear_time_ms, completed_at, affixes
│   │       ├── MythicPlusScore.java          # record: season, scores Map<String, Double>, segments
│   │       ├── TalentLoadout.java            # record: loadout_text, spec_id, class_id
│   │       ├── RaidProgression.java          # record: summary, total_bosses, normal_bosses_killed, heroic_bosses_killed, mythic_bosses_killed
│   │       ├── AffixesResponse.java          # record: region, title, affix_details List<AffixDetail>
│   │       ├── AffixDetail.java              # record: id, name, description
│   │       └── MythicPlusLeaderboardResponse.java  # record: rankings List<LeaderboardRun>
│   └── tools/
│       ├── CharacterAuditTool.java           # @Tool wow_character_audit
│       ├── EvaluatePlayerTool.java           # @Tool wow_evaluate_player
│       ├── WeeklyPlanTool.java               # @Tool wow_weekly_plan
│       ├── FindUpgradesTool.java             # @Tool wow_find_upgrades
│       ├── CompareToMetaTool.java            # @Tool wow_compare_to_meta
│       └── format/
│           └── TextFormatter.java            # Shared helpers: section headers, bullet lists, percentages
├── src/main/resources/
│   ├── application.yml
│   └── logback-spring.xml                    # CRITICAL: route all logs to stderr (STDIO protocol safety)
└── src/test/
    ├── java/dev/benjamin/wow/raiderio/
    │   ├── client/RaiderioClientTest.java
    │   ├── tools/CharacterAuditToolTest.java
    │   ├── tools/EvaluatePlayerToolTest.java
    │   ├── tools/WeeklyPlanToolTest.java
    │   ├── tools/FindUpgradesToolTest.java
    │   ├── tools/CompareToMetaToolTest.java
    │   └── support/WireMockExtension.java     # Registers @RegisterExtension + default port
    └── resources/fixtures/
        ├── character-fury-warrior.json        # Realistic full profile
        ├── character-notfound.json            # Raider.io 404 shape
        ├── affixes-week.json
        └── leaderboard-runs.json
```

**Responsibility boundaries:**
- `client` knows HTTP. No domain logic, no text formatting.
- `tools` know how to aggregate + summarize. No raw WebClient calls.
- `config` knows beans. No business logic.

---

### Task 1: Project bootstrap (Maven, main class, application.yml, logback)

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/dev/benjamin/wow/raiderio/RaiderioMcpApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/logback-spring.xml`
- Create: `.gitignore`

- [ ] **Step 1: Create `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
        <relativePath/>
    </parent>
    <groupId>dev.benjamin.wow</groupId>
    <artifactId>wow-raiderio-mcp</artifactId>
    <version>1.0.0</version>
    <name>wow-raiderio-mcp</name>
    <properties>
        <java.version>21</java.version>
        <spring-ai.version>1.0.3</spring-ai.version>
        <wiremock.version>3.9.2</wiremock.version>
    </properties>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-mcp-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-cache</artifactId>
        </dependency>
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.wiremock</groupId>
            <artifactId>wiremock-standalone</artifactId>
            <version>${wiremock.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <finalName>wow-raiderio-mcp</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create main application class**

```java
package dev.benjamin.wow.raiderio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class RaiderioMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(RaiderioMcpApplication.class, args);
    }
}
```

- [ ] **Step 3: Create `application.yml`**

```yaml
spring:
  application:
    name: wow-raiderio-mcp
  main:
    web-application-type: none
    banner-mode: off
  ai:
    mcp:
      server:
        name: wow-raiderio-mcp
        version: 1.0.0
        type: SYNC

raiderio:
  base-url: https://raider.io/api/v1
  default-region: eu
  timeout-seconds: 10

logging:
  level:
    root: ERROR
    dev.benjamin.wow: INFO
```

- [ ] **Step 4: Create `logback-spring.xml` (STDERR-only — critical for STDIO)**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
        <target>System.err</target>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="ERROR">
        <appender-ref ref="STDERR"/>
    </root>
    <logger name="dev.benjamin.wow" level="INFO"/>
</configuration>
```

Why: STDIO MCP transport uses stdout for JSON-RPC frames. A single log line on stdout corrupts the stream.

- [ ] **Step 5: Create `.gitignore`**

```
target/
.idea/
*.iml
.vscode/
.DS_Store
```

- [ ] **Step 6: Verify build compiles**

Run: `cd /Users/benjamin/Documents/wow-raiderio-mcp && mvn -q compile`
Expected: BUILD SUCCESS, no errors.

- [ ] **Step 7: Commit**

```bash
cd /Users/benjamin/Documents/wow-raiderio-mcp
git init
git add .
git commit -m "chore: bootstrap Spring Boot MCP server project"
```

---

### Task 2: DTOs (Jackson records for Raider.io responses)

**Files:**
- Create: `src/main/java/dev/benjamin/wow/raiderio/client/dto/CharacterProfile.java` (+ 9 other DTO records)
- Create: `src/test/resources/fixtures/character-fury-warrior.json`
- Create: `src/test/resources/fixtures/affixes-week.json`
- Create: `src/test/resources/fixtures/leaderboard-runs.json`
- Test: `src/test/java/dev/benjamin/wow/raiderio/client/dto/DtoDeserializationTest.java`

- [ ] **Step 1: Create realistic fixture `character-fury-warrior.json`**

Paste a realistic Raider.io response. Include these fields (from a real call):
- `name`, `race`, `class`, `active_spec_name`, `active_spec_role`, `region`, `realm`, `faction`, `thumbnail_url`
- `mythic_plus_scores_by_season`: array with one element `{ "season": "season-tww-3", "scores": { "all": 2850.5, "dps": 2850.5, "healer": 0, "tank": 0 }, "segments": {...} }`
- `mythic_plus_best_runs`: array of ~8 runs, each with `dungeon`, `short_name`, `mythic_level`, `num_keystone_upgrades` (0=depleted, 1-3=timed), `score`, `clear_time_ms`, `par_time_ms`, `completed_at`, `affixes` (array of `{name}`)
- `mythic_plus_recent_runs`: array of ~10 runs (same shape)
- `gear.item_level_equipped`: e.g. 636.8
- `gear.items`: map where key is slot name (`head`, `neck`, `shoulder`, `back`, `chest`, `wrist`, `hands`, `waist`, `legs`, `feet`, `finger1`, `finger2`, `trinket1`, `trinket2`, `mainhand`, `offhand`) → `{ item_id, item_level, name, enchant (int, 0 = none), gems (array of ints, empty = no gem), tier (optional) }`
- `talents.loadout_text`: long base64 string
- `raid_progression."nerub-ar-palace".summary`: e.g. `"8/8 H"`

Fixture must have: at least 2 slots with enchant=0 (where enchant expected), at least 1 socket slot without gem, and recent runs mixing timed (num_keystone_upgrades >= 1) and depleted (0) runs.

- [ ] **Step 2: Write failing deserialization test**

```java
package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class DtoDeserializationTest {
    private final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void deserializesFullCharacterProfile() throws Exception {
        String json = Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json"));
        CharacterProfile profile = mapper.readValue(json, CharacterProfile.class);

        assertThat(profile.name()).isNotBlank();
        assertThat(profile.characterClass()).isEqualTo("Warrior");
        assertThat(profile.activeSpecName()).isEqualTo("Fury");
        assertThat(profile.gear().itemLevelEquipped()).isGreaterThan(600);
        assertThat(profile.gear().items()).isNotEmpty();
        assertThat(profile.mythicPlusBestRuns()).isNotEmpty();
        assertThat(profile.mythicPlusRecentRuns()).isNotEmpty();
    }
}
```

- [ ] **Step 3: Run test — expect compile failure (DTOs don't exist)**

Run: `mvn -q test -Dtest=DtoDeserializationTest`
Expected: Compile error.

- [ ] **Step 4: Create DTO records**

`CharacterProfile.java`:

```java
package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CharacterProfile(
    String name,
    String race,
    @JsonProperty("class") String characterClass,
    @JsonProperty("active_spec_name") String activeSpecName,
    @JsonProperty("active_spec_role") String activeSpecRole,
    String region,
    String realm,
    String faction,
    @JsonProperty("thumbnail_url") String thumbnailUrl,
    @JsonProperty("mythic_plus_scores_by_season") List<MythicPlusScore> mythicPlusScoresBySeason,
    @JsonProperty("mythic_plus_best_runs") List<MythicPlusRun> mythicPlusBestRuns,
    @JsonProperty("mythic_plus_recent_runs") List<MythicPlusRun> mythicPlusRecentRuns,
    @JsonProperty("mythic_plus_alternate_runs") List<MythicPlusRun> mythicPlusAlternateRuns,
    GearSummary gear,
    TalentLoadout talents,
    @JsonProperty("raid_progression") java.util.Map<String, RaidProgression> raidProgression
) {}
```

`GearSummary.java`:

```java
package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record GearSummary(
    @JsonProperty("item_level_equipped") double itemLevelEquipped,
    @JsonProperty("item_level_total") Double itemLevelTotal,
    Map<String, GearItem> items
) {}
```

`GearItem.java`:

```java
package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GearItem(
    @JsonProperty("item_id") Long itemId,
    @JsonProperty("item_level") Integer itemLevel,
    String name,
    Integer enchant,
    List<Long> gems,
    String tier
) {}
```

`MythicPlusRun.java`:

```java
package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

public record MythicPlusRun(
    String dungeon,
    @JsonProperty("short_name") String shortName,
    @JsonProperty("mythic_level") int mythicLevel,
    @JsonProperty("num_keystone_upgrades") int numKeystoneUpgrades,
    double score,
    @JsonProperty("clear_time_ms") long clearTimeMs,
    @JsonProperty("par_time_ms") Long parTimeMs,
    @JsonProperty("completed_at") OffsetDateTime completedAt,
    List<Affix> affixes
) {
    public record Affix(String name) {}
    public boolean isTimed() { return numKeystoneUpgrades >= 1; }
    public boolean isDepleted() { return numKeystoneUpgrades == 0; }
}
```

`MythicPlusScore.java`:

```java
package dev.benjamin.wow.raiderio.client.dto;

import java.util.Map;

public record MythicPlusScore(
    String season,
    Map<String, Double> scores,
    Map<String, Object> segments
) {}
```

`TalentLoadout.java`:

```java
package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TalentLoadout(
    @JsonProperty("loadout_text") String loadoutText,
    @JsonProperty("loadout_spec_id") Integer loadoutSpecId,
    @JsonProperty("class_id") Integer classId
) {}
```

`RaidProgression.java`:

```java
package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RaidProgression(
    String summary,
    @JsonProperty("total_bosses") Integer totalBosses,
    @JsonProperty("normal_bosses_killed") Integer normalBossesKilled,
    @JsonProperty("heroic_bosses_killed") Integer heroicBossesKilled,
    @JsonProperty("mythic_bosses_killed") Integer mythicBossesKilled
) {}
```

`AffixesResponse.java`:

```java
package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AffixesResponse(
    String region,
    String title,
    @JsonProperty("leaderboard_url") String leaderboardUrl,
    @JsonProperty("affix_details") List<AffixDetail> affixDetails
) {
    public record AffixDetail(Integer id, String name, String description) {}
}
```

`MythicPlusLeaderboardResponse.java`:

```java
package dev.benjamin.wow.raiderio.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MythicPlusLeaderboardResponse(List<Ranking> rankings) {
    public record Ranking(
        Integer rank,
        Run run
    ) {}
    public record Run(
        String dungeon,
        @JsonProperty("mythic_level") Integer mythicLevel,
        Double score,
        @JsonProperty("clear_time_ms") Long clearTimeMs,
        List<RosterMember> roster
    ) {}
    public record RosterMember(
        Character character
    ) {}
    public record Character(
        String name,
        @JsonProperty("class") String characterClass,
        String spec,
        String race
    ) {}
}
```

- [ ] **Step 5: Run test — expect PASS**

Run: `mvn -q test -Dtest=DtoDeserializationTest`
Expected: PASS.

- [ ] **Step 6: Create other fixtures**

`affixes-week.json`: realistic output of `/mythic-plus/affixes?region=eu&locale=en`.
`leaderboard-runs.json`: realistic output of `/mythic-plus/runs?season=season-tww-3&region=world&dungeon=all&page=0`.

- [ ] **Step 7: Commit**

```bash
git add .
git commit -m "feat: add Raider.io DTO records with fixture-based deserialization test"
```

---

### Task 3: RaiderioClient with WebClient + error handling + WireMock tests

**Files:**
- Create: `src/main/java/dev/benjamin/wow/raiderio/client/RaiderioException.java`
- Create: `src/main/java/dev/benjamin/wow/raiderio/config/RaiderioConfig.java`
- Create: `src/main/java/dev/benjamin/wow/raiderio/client/RaiderioClient.java`
- Test: `src/test/java/dev/benjamin/wow/raiderio/client/RaiderioClientTest.java`

- [ ] **Step 1: Write failing test using WireMock**

```java
package dev.benjamin.wow.raiderio.client;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import dev.benjamin.wow.raiderio.client.dto.AffixesResponse;
import dev.benjamin.wow.raiderio.client.dto.MythicPlusLeaderboardResponse;
import dev.benjamin.wow.raiderio.config.RaiderioConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RaiderioClientTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    private RaiderioClient client() {
        WebClient webClient = WebClient.builder().baseUrl(wm.baseUrl()).build();
        return new RaiderioClient(webClient, new RaiderioConfig.Properties(wm.baseUrl(), "eu", 10));
    }

    @Test
    void fetchesCharacterProfileWithFields() throws Exception {
        String body = Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json"));
        wm.stubFor(get(urlPathEqualTo("/characters/profile"))
            .withQueryParam("region", equalTo("eu"))
            .withQueryParam("realm", equalTo("hyjal"))
            .withQueryParam("name", equalTo("Stormrage"))
            .withQueryParam("fields", matching(".*gear.*"))
            .willReturn(okJson(body)));

        CharacterProfile p = client().fetchCharacterProfile("eu", "hyjal", "Stormrage",
            "mythic_plus_scores_by_season:current,mythic_plus_best_runs,gear");

        assertThat(p.name()).isNotBlank();
        assertThat(p.gear().items()).isNotEmpty();
    }

    @Test
    void throwsOnCharacterNotFound() {
        wm.stubFor(get(urlPathEqualTo("/characters/profile"))
            .willReturn(aResponse().withStatus(400)
                .withBody("{\"statusCode\":400,\"error\":\"Bad Request\",\"message\":\"Could not find requested character\"}")));

        assertThatThrownBy(() -> client().fetchCharacterProfile("eu", "x", "nope", "gear"))
            .isInstanceOf(RaiderioException.class)
            .hasMessageContaining("Could not find");
    }

    @Test
    void fetchesWeeklyAffixes() {
        wm.stubFor(get(urlPathEqualTo("/mythic-plus/affixes"))
            .willReturn(okJson("{\"region\":\"eu\",\"title\":\"Fortified, Bolstering, Raging\",\"leaderboard_url\":\"x\",\"affix_details\":[{\"id\":10,\"name\":\"Fortified\",\"description\":\"...\"}]}")));

        AffixesResponse a = client().fetchAffixes("eu", "en");
        assertThat(a.affixDetails()).hasSize(1);
        assertThat(a.title()).contains("Fortified");
    }

    @Test
    void fetchesLeaderboardRuns() {
        wm.stubFor(get(urlPathEqualTo("/mythic-plus/runs"))
            .willReturn(okJson("{\"rankings\":[{\"rank\":1,\"run\":{\"dungeon\":\"Ara-Kara\",\"mythic_level\":20,\"score\":500.0,\"clear_time_ms\":1700000,\"roster\":[]}}]}")));

        MythicPlusLeaderboardResponse r = client().fetchLeaderboardRuns("world", "season-tww-3", "all", 0);
        assertThat(r.rankings()).hasSize(1);
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

Run: `mvn -q test -Dtest=RaiderioClientTest`
Expected: compile errors (classes missing).

- [ ] **Step 3: Create `RaiderioException`**

```java
package dev.benjamin.wow.raiderio.client;

public class RaiderioException extends RuntimeException {
    public RaiderioException(String message) { super(message); }
    public RaiderioException(String message, Throwable cause) { super(message, cause); }
}
```

- [ ] **Step 4: Create `RaiderioConfig`**

```java
package dev.benjamin.wow.raiderio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(RaiderioConfig.Properties.class)
public class RaiderioConfig {

    @ConfigurationProperties(prefix = "raiderio")
    public record Properties(String baseUrl, String defaultRegion, int timeoutSeconds) {}

    @Bean
    public WebClient raiderioWebClient(Properties props) {
        return WebClient.builder()
            .baseUrl(props.baseUrl())
            .defaultHeader("User-Agent", "wow-raiderio-mcp/1.0")
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
    }
}
```

- [ ] **Step 5: Create `RaiderioClient`**

```java
package dev.benjamin.wow.raiderio.client;

import dev.benjamin.wow.raiderio.client.dto.AffixesResponse;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import dev.benjamin.wow.raiderio.client.dto.MythicPlusLeaderboardResponse;
import dev.benjamin.wow.raiderio.config.RaiderioConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

@Component
public class RaiderioClient {

    private final WebClient webClient;
    private final Duration timeout;

    public RaiderioClient(WebClient raiderioWebClient, RaiderioConfig.Properties props) {
        this.webClient = raiderioWebClient;
        this.timeout = Duration.ofSeconds(props.timeoutSeconds());
    }

    @Cacheable(cacheNames = "characterProfile", key = "#region + ':' + #realm + ':' + #name + ':' + #fields")
    public CharacterProfile fetchCharacterProfile(String region, String realm, String name, String fields) {
        return get("/characters/profile", CharacterProfile.class, uri -> uri
            .queryParam("region", region)
            .queryParam("realm", realm)
            .queryParam("name", name)
            .queryParam("fields", fields));
    }

    @Cacheable(cacheNames = "affixes", key = "#region + ':' + #locale")
    public AffixesResponse fetchAffixes(String region, String locale) {
        return get("/mythic-plus/affixes", AffixesResponse.class, uri -> uri
            .queryParam("region", region)
            .queryParam("locale", locale));
    }

    @Cacheable(cacheNames = "leaderboard", key = "#region + ':' + #season + ':' + #dungeon + ':' + #page")
    public MythicPlusLeaderboardResponse fetchLeaderboardRuns(String region, String season, String dungeon, int page) {
        return get("/mythic-plus/runs", MythicPlusLeaderboardResponse.class, uri -> uri
            .queryParam("region", region)
            .queryParam("season", season)
            .queryParam("dungeon", dungeon)
            .queryParam("page", page));
    }

    private <T> T get(String path, Class<T> type,
                      java.util.function.Function<org.springframework.web.util.UriBuilder,
                                                  org.springframework.web.util.UriBuilder> q) {
        try {
            return webClient.get()
                .uri(b -> q.apply(b.path(path)).build())
                .retrieve()
                .bodyToMono(type)
                .block(timeout);
        } catch (WebClientResponseException e) {
            String body = e.getResponseBodyAsString();
            throw new RaiderioException("Raider.io " + e.getStatusCode() + ": " + body, e);
        } catch (Exception e) {
            throw new RaiderioException("Raider.io call failed: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 6: Run test — expect PASS**

Run: `mvn -q test -Dtest=RaiderioClientTest`
Expected: 4 tests pass.

- [ ] **Step 7: Commit**

```bash
git add .
git commit -m "feat: add RaiderioClient with WebClient, exception handling, WireMock tests"
```

---

### Task 4: CacheConfig (Caffeine with 3 named caches and TTLs)

**Files:**
- Create: `src/main/java/dev/benjamin/wow/raiderio/config/CacheConfig.java`
- Test: `src/test/java/dev/benjamin/wow/raiderio/config/CacheConfigTest.java`

- [ ] **Step 1: Write failing test**

```java
package dev.benjamin.wow.raiderio.config;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.benjamin.wow.raiderio.client.RaiderioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = { "spring.main.web-application-type=none" })
class CacheConfigTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("raiderio.base-url", wm::baseUrl);
    }

    @Autowired RaiderioClient client;
    @Autowired CacheManager cacheManager;

    @Test
    void cacheNamesAreRegistered() {
        assertThat(cacheManager.getCacheNames())
            .contains("characterProfile", "affixes", "leaderboard");
    }

    @Test
    void secondCallHitsCacheAndDoesNotReRequest() throws Exception {
        String body = Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json"));
        wm.stubFor(get(urlPathEqualTo("/characters/profile")).willReturn(okJson(body)));

        client.fetchCharacterProfile("eu", "hyjal", "Stormrage", "gear");
        client.fetchCharacterProfile("eu", "hyjal", "Stormrage", "gear");

        wm.verify(1, getRequestedFor(urlPathEqualTo("/characters/profile")));
    }
}
```

- [ ] **Step 2: Run test — expect failure (caches not configured)**

Run: `mvn -q test -Dtest=CacheConfigTest`
Expected: `cacheNamesAreRegistered` FAIL; `secondCallHitsCache` likely FAIL (2 requests made).

- [ ] **Step 3: Create `CacheConfig`**

```java
package dev.benjamin.wow.raiderio.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager mgr = new SimpleCacheManager();
        mgr.setCaches(List.of(
            caffeine("characterProfile", Duration.ofMinutes(5), 500),
            caffeine("affixes",          Duration.ofHours(1),  4),
            caffeine("leaderboard",      Duration.ofMinutes(15), 200)
        ));
        return mgr;
    }

    private CaffeineCache caffeine(String name, Duration ttl, int maxSize) {
        return new CaffeineCache(name,
            Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build());
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

Run: `mvn -q test -Dtest=CacheConfigTest`
Expected: 2 tests pass. WireMock should have received exactly 1 request.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: add Caffeine cache config (profile 5m, affixes 1h, leaderboard 15m)"
```

---

### Task 5: TextFormatter helper (shared formatting)

**Files:**
- Create: `src/main/java/dev/benjamin/wow/raiderio/tools/format/TextFormatter.java`
- Test: `src/test/java/dev/benjamin/wow/raiderio/tools/format/TextFormatterTest.java`

- [ ] **Step 1: Write failing test**

```java
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
    }

    @Test
    void formatPercentRoundsToOneDecimal() {
        assertThat(TextFormatter.pct(0.8333)).isEqualTo("83.3%");
        assertThat(TextFormatter.pct(1.0)).isEqualTo("100.0%");
    }
}
```

- [ ] **Step 2: Run — expect compile failure.**

- [ ] **Step 3: Implement `TextFormatter`**

```java
package dev.benjamin.wow.raiderio.tools.format;

public class TextFormatter {
    private final StringBuilder sb = new StringBuilder();

    public TextFormatter section(String title) {
        if (!sb.isEmpty()) sb.append('\n');
        sb.append("## ").append(title).append('\n');
        return this;
    }
    public TextFormatter line(String text) { sb.append(text).append('\n'); return this; }
    public TextFormatter bullet(String text) { sb.append("- ").append(text).append('\n'); return this; }
    public TextFormatter blank() { sb.append('\n'); return this; }

    @Override public String toString() { return sb.toString(); }

    public static String pct(double ratio) { return String.format("%.1f%%", ratio * 100.0); }
}
```

- [ ] **Step 4: Run — expect PASS.** `mvn -q test -Dtest=TextFormatterTest`

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: add TextFormatter helper for tool output"
```

---

### Task 6: CharacterAuditTool (wow_character_audit)

**Files:**
- Create: `src/main/java/dev/benjamin/wow/raiderio/tools/CharacterAuditTool.java`
- Test: `src/test/java/dev/benjamin/wow/raiderio/tools/CharacterAuditToolTest.java`

Behavior:
- Fetch profile with fields `mythic_plus_scores_by_season:current,mythic_plus_best_runs,mythic_plus_recent_runs,gear,talents,raid_progression`.
- Slots where an enchant is EXPECTED (hard-coded): `mainhand`, `offhand`, `finger1`, `finger2`, `back`, `legs`, `feet`, `wrist`, `chest`. Report any with `enchant == 0`.
- Missing gems: any item with a tier/socket expectation where `gems` is empty. For simplicity, report items whose `gems` list is empty AND whose slot is in `{ head, neck, wrist, waist, finger1, finger2 }` (typical socket candidates in current season — document this as heuristic).
- Metrics: total score (from `mythic_plus_scores_by_season[0].scores.all`), best & worst dungeon by score from `mythic_plus_best_runs`, timed ratio = `recent.stream().filter(isTimed).count() / recent.size()`, equipped ilvl.

- [ ] **Step 1: Write failing test**

```java
package dev.benjamin.wow.raiderio.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CharacterAuditToolTest {
    private final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void rendersAuditWithScoreGearEnchantsAndTimedRatio() throws Exception {
        CharacterProfile profile = mapper.readValue(
            Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json")),
            CharacterProfile.class);

        RaiderioClient client = mock(RaiderioClient.class);
        when(client.fetchCharacterProfile(eq("eu"), eq("hyjal"), eq("Stormrage"), any()))
            .thenReturn(profile);

        String out = new CharacterAuditTool(client).wow_character_audit("Stormrage", "hyjal", "eu");

        assertThat(out).contains("Stormrage");
        assertThat(out).contains("Fury Warrior");
        assertThat(out).contains("M+ Score");
        assertThat(out).contains("Equipped iLvl");
        assertThat(out).contains("Missing enchants");   // section exists
        assertThat(out).contains("Best runs");
        assertThat(out).contains("Recent timed ratio");
    }
}
```

- [ ] **Step 2: Run — expect compile failure.**

- [ ] **Step 3: Implement `CharacterAuditTool`**

```java
package dev.benjamin.wow.raiderio.tools;

import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import dev.benjamin.wow.raiderio.client.dto.GearItem;
import dev.benjamin.wow.raiderio.client.dto.MythicPlusRun;
import dev.benjamin.wow.raiderio.tools.format.TextFormatter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CharacterAuditTool {

    private static final Set<String> ENCHANT_SLOTS = Set.of(
        "mainhand", "offhand", "finger1", "finger2", "back", "legs", "feet", "wrist", "chest");
    private static final Set<String> SOCKET_SLOTS = Set.of(
        "head", "neck", "wrist", "waist", "finger1", "finger2");
    private static final String FIELDS =
        "mythic_plus_scores_by_season:current,mythic_plus_best_runs,mythic_plus_recent_runs,gear,talents,raid_progression";

    private final RaiderioClient client;
    public CharacterAuditTool(RaiderioClient client) { this.client = client; }

    @Tool(name = "wow_character_audit",
          description = "Audit complet d'un personnage WoW : score M+, stuff, enchants, gems, talents, runs récents et meilleurs runs. Identifie les faiblesses et axes d'amélioration.")
    public String wow_character_audit(
            @ToolParam(description = "Character name") String name,
            @ToolParam(description = "Realm slug (e.g., 'hyjal')") String realm,
            @ToolParam(description = "Region: eu, us, kr, tw") String region) {

        String reg = (region == null || region.isBlank()) ? "eu" : region.toLowerCase();
        CharacterProfile p = client.fetchCharacterProfile(reg, realm, name, FIELDS);

        TextFormatter f = new TextFormatter();
        f.section(p.name() + " — " + p.activeSpecName() + " " + p.characterClass() + " (" + reg + "/" + p.realm() + ")");

        double score = p.mythicPlusScoresBySeason() != null && !p.mythicPlusScoresBySeason().isEmpty()
            ? p.mythicPlusScoresBySeason().get(0).scores().getOrDefault("all", 0.0) : 0.0;
        f.line("M+ Score (current season): " + String.format("%.0f", score));
        if (p.gear() != null) {
            f.line("Equipped iLvl: " + String.format("%.1f", p.gear().itemLevelEquipped()));
        }
        if (p.raidProgression() != null && !p.raidProgression().isEmpty()) {
            var tier = p.raidProgression().values().iterator().next();
            f.line("Raid progression: " + tier.summary());
        }

        // Missing enchants
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
        if (missing.isEmpty()) f.line("None — all enchantable slots are enchanted.");
        else missing.forEach(f::bullet);

        // Missing gems (heuristic)
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
        if (noGem.isEmpty()) f.line("None detected.");
        else noGem.forEach(f::bullet);

        // Best & worst
        f.section("Best runs");
        List<MythicPlusRun> best = Optional.ofNullable(p.mythicPlusBestRuns()).orElse(List.of());
        if (best.isEmpty()) f.line("No best runs recorded.");
        else best.stream()
            .sorted(Comparator.comparingDouble(MythicPlusRun::score).reversed())
            .limit(5)
            .forEach(r -> f.bullet(r.dungeon() + " +" + r.mythicLevel()
                + " (" + (r.isTimed() ? "timed" : "depleted") + ", score " + String.format("%.0f", r.score()) + ")"));

        MythicPlusRun worst = best.stream().min(Comparator.comparingDouble(MythicPlusRun::score)).orElse(null);
        if (worst != null) {
            f.section("Weakest dungeon");
            f.line(worst.dungeon() + " +" + worst.mythicLevel() + " — score " + String.format("%.0f", worst.score())
                + ". Prioritize this for the biggest score gain.");
        }

        // Recent timed ratio
        f.section("Recent timed ratio");
        List<MythicPlusRun> recent = Optional.ofNullable(p.mythicPlusRecentRuns()).orElse(List.of());
        if (recent.isEmpty()) f.line("No recent runs.");
        else {
            long timed = recent.stream().filter(MythicPlusRun::isTimed).count();
            f.line(timed + "/" + recent.size() + " timed (" + TextFormatter.pct((double) timed / recent.size()) + ")");
        }

        return f.toString();
    }
}
```

- [ ] **Step 4: Run — expect PASS.** `mvn -q test -Dtest=CharacterAuditToolTest`

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat(tool): add wow_character_audit"
```

---

### Task 7: EvaluatePlayerTool (wow_evaluate_player)

**Files:**
- Create: `src/main/java/dev/benjamin/wow/raiderio/tools/EvaluatePlayerTool.java`
- Test: `src/test/java/dev/benjamin/wow/raiderio/tools/EvaluatePlayerToolTest.java`

Behavior:
- Fetch profile with `mythic_plus_scores_by_season:current,mythic_plus_best_runs,mythic_plus_recent_runs,gear`.
- Consistency = `timed / total` of recent runs.
- Optional `dungeon` filter: look at best runs matching `dungeon` case-insensitively (on `dungeon` or `shortName`) and report max timed level.
- Class utilities: lookup table (Warrior: Battle Shout, Rallying Cry; Evoker: Bronze, Time Dilation; Priest: Battle Rez if spec=Discipline/Holy; Mage: Bloodlust-equivalent = Time Warp; etc.). Keep to a tight map — educational content, not gameplay authority.
- Output: verdict line ("Strong candidate for +15 keys" etc.), score, equipped ilvl, consistency, dungeon-specific max (if asked), utilities.

- [ ] **Step 1: Failing test**

```java
package dev.benjamin.wow.raiderio.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluatePlayerToolTest {
    private final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void rendersVerdictScoreConsistencyAndUtilities() throws Exception {
        CharacterProfile p = mapper.readValue(
            Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json")),
            CharacterProfile.class);
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(p);

        String out = new EvaluatePlayerTool(c).wow_evaluate_player("Stormrage", "hyjal", "eu", null);
        assertThat(out).contains("Verdict");
        assertThat(out).contains("M+ Score");
        assertThat(out).contains("Consistency");
        assertThat(out).contains("Utilities");
        assertThat(out).containsIgnoringCase("Battle Shout");
    }

    @Test
    void filtersDungeonWhenProvided() throws Exception {
        CharacterProfile p = mapper.readValue(
            Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json")),
            CharacterProfile.class);
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(p);

        String out = new EvaluatePlayerTool(c).wow_evaluate_player("Stormrage", "hyjal", "eu", p.mythicPlusBestRuns().get(0).dungeon());
        assertThat(out).contains("Dungeon experience");
    }
}
```

- [ ] **Step 2: Run — expect compile fail.**

- [ ] **Step 3: Implement `EvaluatePlayerTool`**

```java
package dev.benjamin.wow.raiderio.tools;

import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import dev.benjamin.wow.raiderio.client.dto.MythicPlusRun;
import dev.benjamin.wow.raiderio.tools.format.TextFormatter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class EvaluatePlayerTool {

    private static final Map<String, List<String>> CLASS_UTILITIES = Map.ofEntries(
        Map.entry("Warrior",     List.of("Battle Shout (+5% attack power)", "Rallying Cry (raid CD)", "Pummel (interrupt)", "Shattering Throw (immunity removal)")),
        Map.entry("Paladin",     List.of("Blessing of the Bronze/Summer/etc.", "Hand of Freedom", "Battle Rez (Holy: NO; Ret/Prot: Lay on Hands)", "Rebuke (interrupt)")),
        Map.entry("Hunter",      List.of("Mark of the Wild via pet (no)", "Tranq Shot (enrage/magic dispel)", "Misdirection", "Counter Shot (interrupt)")),
        Map.entry("Rogue",       List.of("Shroud of Concealment (skip)", "Kick (interrupt)", "Tricks of the Trade", "Smoke Bomb")),
        Map.entry("Priest",      List.of("Power Word: Fortitude (raid buff)", "Mass Dispel", "Mind Soothe", "Silence (Shadow: interrupt)")),
        Map.entry("Death Knight",List.of("Anti-Magic Zone", "Mind Freeze (interrupt)", "Death Grip", "Battle Rez via Raise Ally")),
        Map.entry("Shaman",      List.of("Bloodlust/Heroism", "Purge", "Wind Shear (interrupt)", "Spirit Link Totem (Resto)")),
        Map.entry("Mage",        List.of("Time Warp (Bloodlust)", "Arcane Intellect", "Counterspell (interrupt)", "Spellsteal")),
        Map.entry("Warlock",     List.of("Summon", "Battle Rez via Soulstone", "Spell Lock (pet interrupt)", "Curse dispel")),
        Map.entry("Monk",        List.of("Mystic Touch (+5% physical damage taken by target)", "Spear Hand Strike (interrupt)", "Revival (Mistweaver)", "Ring of Peace")),
        Map.entry("Druid",       List.of("Mark of the Wild", "Soothe (enrage dispel)", "Skull Bash/Solar Beam (interrupt)", "Battle Rez (Rebirth)")),
        Map.entry("Demon Hunter",List.of("Chaos Brand (+5% magic damage taken)", "Disrupt (interrupt)", "Darkness (raid CD)", "Imprison")),
        Map.entry("Evoker",      List.of("Blessing of the Bronze", "Time Dilation", "Quell (interrupt)", "Rescue"))
    );
    private static final String FIELDS = "mythic_plus_scores_by_season:current,mythic_plus_best_runs,mythic_plus_recent_runs,gear";

    private final RaiderioClient client;
    public EvaluatePlayerTool(RaiderioClient client) { this.client = client; }

    @Tool(name = "wow_evaluate_player",
          description = "Évalue rapidement un joueur WoW pour décider s'il est un bon candidat pour un groupe M+. Retourne score, consistance, expérience par donjon et utilitaires de classe.")
    public String wow_evaluate_player(
            @ToolParam(description = "Character name") String name,
            @ToolParam(description = "Realm slug") String realm,
            @ToolParam(description = "Region: eu, us, kr, tw") String region,
            @ToolParam(description = "Optional dungeon name or short code to filter experience on", required = false) String dungeon) {

        String reg = (region == null || region.isBlank()) ? "eu" : region.toLowerCase();
        CharacterProfile p = client.fetchCharacterProfile(reg, realm, name, FIELDS);

        TextFormatter f = new TextFormatter();
        f.section(p.name() + " — " + p.activeSpecName() + " " + p.characterClass());

        double score = p.mythicPlusScoresBySeason() != null && !p.mythicPlusScoresBySeason().isEmpty()
            ? p.mythicPlusScoresBySeason().get(0).scores().getOrDefault("all", 0.0) : 0.0;
        f.line("M+ Score: " + String.format("%.0f", score));
        if (p.gear() != null) f.line("Equipped iLvl: " + String.format("%.1f", p.gear().itemLevelEquipped()));

        f.section("Consistency");
        List<MythicPlusRun> recent = Optional.ofNullable(p.mythicPlusRecentRuns()).orElse(List.of());
        if (recent.isEmpty()) f.line("No recent runs on record.");
        else {
            long timed = recent.stream().filter(MythicPlusRun::isTimed).count();
            f.line(timed + "/" + recent.size() + " timed (" + TextFormatter.pct((double) timed / recent.size()) + ")");
        }

        if (dungeon != null && !dungeon.isBlank()) {
            f.section("Dungeon experience: " + dungeon);
            int maxTimed = Optional.ofNullable(p.mythicPlusBestRuns()).orElse(List.of()).stream()
                .filter(r -> r.dungeon() != null && r.dungeon().toLowerCase().contains(dungeon.toLowerCase())
                    || r.shortName() != null && r.shortName().equalsIgnoreCase(dungeon))
                .filter(MythicPlusRun::isTimed)
                .mapToInt(MythicPlusRun::mythicLevel).max().orElse(-1);
            f.line(maxTimed < 0 ? "No timed runs on this dungeon." : "Max timed key: +" + maxTimed);
        }

        f.section("Utilities");
        List<String> utils = CLASS_UTILITIES.getOrDefault(p.characterClass(), List.of("Unknown class"));
        utils.forEach(f::bullet);

        f.section("Verdict");
        f.line(verdict(score, recent));

        return f.toString();
    }

    private String verdict(double score, List<MythicPlusRun> recent) {
        long timed = recent.stream().filter(MythicPlusRun::isTimed).count();
        double ratio = recent.isEmpty() ? 0 : (double) timed / recent.size();
        if (score >= 3000 && ratio >= 0.75) return "Top-tier candidate — take without hesitation.";
        if (score >= 2500 && ratio >= 0.6)  return "Strong candidate for +13/+15 keys.";
        if (score >= 2000)                   return "Solid for +10 to +12 range.";
        if (score >= 1500)                   return "Entry-level; suitable for +6 to +9.";
        return "Low score — progression player, expect to carry.";
    }
}
```

- [ ] **Step 4: Run — expect PASS.** `mvn -q test -Dtest=EvaluatePlayerToolTest`

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat(tool): add wow_evaluate_player"
```

---

### Task 8: WeeklyPlanTool (wow_weekly_plan)

**Files:**
- Create: `src/main/java/dev/benjamin/wow/raiderio/tools/WeeklyPlanTool.java`
- Test: `src/test/java/dev/benjamin/wow/raiderio/tools/WeeklyPlanToolTest.java`

Behavior:
- Call `fetchAffixes(region, "en")` + `fetchCharacterProfile(..., "mythic_plus_scores_by_season:current,mythic_plus_best_runs,mythic_plus_recent_runs")`.
- For each dungeon in best runs, find its score. Sort ascending (lowest first) — top 4 = priority targets.
- Count recent runs from the last 7 days (based on `completedAt`). Map to Great Vault slots: 1 run = slot 1, 4 runs = slot 2, 8 runs = slot 3. Output progression towards each slot.
- Output: affixes block + priority dungeons + vault status + recommendation line.

- [ ] **Step 1: Failing test**

```java
package dev.benjamin.wow.raiderio.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.AffixesResponse;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeeklyPlanToolTest {
    private final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void rendersAffixesPrioritiesAndVault() throws Exception {
        CharacterProfile p = mapper.readValue(
            Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json")),
            CharacterProfile.class);
        AffixesResponse a = new AffixesResponse("eu", "Fortified, Bolstering, Raging", "url",
            List.of(new AffixesResponse.AffixDetail(10, "Fortified", "...")));

        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(p);
        when(c.fetchAffixes(any(), any())).thenReturn(a);

        String out = new WeeklyPlanTool(c).wow_weekly_plan("Stormrage", "hyjal", "eu");

        assertThat(out).contains("Weekly affixes");
        assertThat(out).contains("Fortified");
        assertThat(out).contains("Priority dungeons");
        assertThat(out).contains("Great Vault");
    }
}
```

- [ ] **Step 2: Run — expect compile fail.**

- [ ] **Step 3: Implement `WeeklyPlanTool`**

```java
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
import java.util.*;
import java.util.stream.Collectors;

@Component
public class WeeklyPlanTool {

    private static final String FIELDS = "mythic_plus_scores_by_season:current,mythic_plus_best_runs,mythic_plus_recent_runs";
    private final RaiderioClient client;
    public WeeklyPlanTool(RaiderioClient client) { this.client = client; }

    @Tool(name = "wow_weekly_plan",
          description = "Planifie la semaine M+ d'un joueur : affixes actuels, dungeons prioritaires pour maximiser le score, progression Great Vault.")
    public String wow_weekly_plan(
            @ToolParam(description = "Character name") String name,
            @ToolParam(description = "Realm slug") String realm,
            @ToolParam(description = "Region: eu, us, kr, tw") String region) {

        String reg = (region == null || region.isBlank()) ? "eu" : region.toLowerCase();
        AffixesResponse a = client.fetchAffixes(reg, "en");
        CharacterProfile p = client.fetchCharacterProfile(reg, realm, name, FIELDS);

        TextFormatter f = new TextFormatter();
        f.section("Weekly affixes (" + reg + ")");
        f.line(a.title());
        if (a.affixDetails() != null) a.affixDetails().forEach(d -> f.bullet(d.name() + " — " + d.description()));

        // Priority dungeons: lowest score first (most room to grow)
        f.section("Priority dungeons (biggest score gain potential)");
        Map<String, Double> bestByDungeon = Optional.ofNullable(p.mythicPlusBestRuns()).orElse(List.of()).stream()
            .collect(Collectors.toMap(MythicPlusRun::dungeon, MythicPlusRun::score, Math::max));
        if (bestByDungeon.isEmpty()) f.line("No best runs — start with any dungeon at a comfortable key level.");
        else bestByDungeon.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .limit(4)
            .forEach(e -> f.bullet(e.getKey() + " — current best score " + String.format("%.0f", e.getValue())));

        // Great Vault
        f.section("Great Vault progression");
        OffsetDateTime weekAgo = OffsetDateTime.now().minusDays(7);
        long runsThisWeek = Optional.ofNullable(p.mythicPlusRecentRuns()).orElse(List.of()).stream()
            .filter(r -> r.completedAt() != null && r.completedAt().isAfter(weekAgo))
            .count();
        f.line("Runs completed this week: " + runsThisWeek);
        f.bullet("Slot 1 (1 run):  " + (runsThisWeek >= 1 ? "UNLOCKED" : "need " + (1 - runsThisWeek)));
        f.bullet("Slot 2 (4 runs): " + (runsThisWeek >= 4 ? "UNLOCKED" : "need " + (4 - runsThisWeek)));
        f.bullet("Slot 3 (8 runs): " + (runsThisWeek >= 8 ? "UNLOCKED" : "need " + (8 - runsThisWeek)));

        f.section("Recommendation");
        if (runsThisWeek < 8) f.line("Focus on reaching 8 runs for full vault. Prioritize the dungeons above.");
        else f.line("Vault locked. Focus on pushing key levels in priority dungeons.");

        return f.toString();
    }
}
```

- [ ] **Step 4: Run — expect PASS.**

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat(tool): add wow_weekly_plan"
```

---

### Task 9: FindUpgradesTool (wow_find_upgrades)

**Files:**
- Create: `src/main/java/dev/benjamin/wow/raiderio/tools/FindUpgradesTool.java`
- Test: `src/test/java/dev/benjamin/wow/raiderio/tools/FindUpgradesToolTest.java`

Behavior:
- Fetch profile with only `gear`.
- Sort all equipped items ascending by `item_level`. Take bottom 5.
- For each: output slot, item name, ilvl, and a "source hint" — a static map suggesting type of source (e.g., "trinkets: check dungeon drop tables or crafted"). No leaderboard lookup.

- [ ] **Step 1: Failing test**

```java
package dev.benjamin.wow.raiderio.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindUpgradesToolTest {
    private final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void listsFiveWeakestSlotsWithSourceHints() throws Exception {
        CharacterProfile p = mapper.readValue(
            Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json")),
            CharacterProfile.class);
        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(p);

        String out = new FindUpgradesTool(c).wow_find_upgrades("Stormrage", "hyjal", "eu");
        assertThat(out).contains("Weakest slots");
        long bullets = out.lines().filter(l -> l.startsWith("- ")).count();
        assertThat(bullets).isBetween(3L, 5L);
    }
}
```

- [ ] **Step 2: Run — expect compile fail.**

- [ ] **Step 3: Implement `FindUpgradesTool`**

```java
package dev.benjamin.wow.raiderio.tools;

import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import dev.benjamin.wow.raiderio.client.dto.GearItem;
import dev.benjamin.wow.raiderio.tools.format.TextFormatter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

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
    public FindUpgradesTool(RaiderioClient client) { this.client = client; }

    @Tool(name = "wow_find_upgrades",
          description = "Identifie les pièces d'équipement les plus faibles d'un personnage et indique où chercher des améliorations (dungeons M+ / raid / craft).")
    public String wow_find_upgrades(
            @ToolParam(description = "Character name") String name,
            @ToolParam(description = "Realm slug") String realm,
            @ToolParam(description = "Region: eu, us, kr, tw") String region) {

        String reg = (region == null || region.isBlank()) ? "eu" : region.toLowerCase();
        CharacterProfile p = client.fetchCharacterProfile(reg, realm, name, "gear");

        TextFormatter f = new TextFormatter();
        f.section(p.name() + " — weakest gear slots");
        if (p.gear() != null) f.line("Equipped iLvl: " + String.format("%.1f", p.gear().itemLevelEquipped()));

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
```

- [ ] **Step 4: Run — expect PASS.**

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat(tool): add wow_find_upgrades"
```

---

### Task 10: CompareToMetaTool (wow_compare_to_meta)

**Files:**
- Create: `src/main/java/dev/benjamin/wow/raiderio/tools/CompareToMetaTool.java`
- Test: `src/test/java/dev/benjamin/wow/raiderio/tools/CompareToMetaToolTest.java`

Behavior:
- Fetch profile with `talents,gear`.
- Fetch leaderboard runs for current season (e.g., `season-tww-3`, region `world`, dungeon `all`, page 0) via `fetchLeaderboardRuns`.
- Filter top runs whose roster contains a player with same class/spec. Count occurrences → top N top-of-the-ladder spec peers.
- Output: player's talents loadout string (raw, disclaimer noted), meta presence count for their spec, hint that full talent comparison requires external tool (e.g., Wowhead).
- Avoid false precision: Raider.io doesn't expose per-talent decoding here, so we report spec representation in meta and player's loadout text opaquely.

- [ ] **Step 1: Failing test**

```java
package dev.benjamin.wow.raiderio.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import dev.benjamin.wow.raiderio.client.dto.MythicPlusLeaderboardResponse;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompareToMetaToolTest {
    private final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void reportsSpecPresenceInLeaderboard() throws Exception {
        CharacterProfile p = mapper.readValue(
            Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json")),
            CharacterProfile.class);

        MythicPlusLeaderboardResponse lb = new MythicPlusLeaderboardResponse(List.of(
            new MythicPlusLeaderboardResponse.Ranking(1, new MythicPlusLeaderboardResponse.Run(
                "Ara-Kara", 20, 500.0, 1700000L, List.of(
                    new MythicPlusLeaderboardResponse.RosterMember(
                        new MythicPlusLeaderboardResponse.Character("Top1", "Warrior", "Fury", "Orc"))
                )))
        ));

        RaiderioClient c = mock(RaiderioClient.class);
        when(c.fetchCharacterProfile(any(), any(), any(), any())).thenReturn(p);
        when(c.fetchLeaderboardRuns(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(lb);

        String out = new CompareToMetaTool(c).wow_compare_to_meta("Stormrage", "hyjal", "eu");
        assertThat(out).contains("Meta presence");
        assertThat(out).contains("Fury Warrior");
    }
}
```

- [ ] **Step 2: Run — expect compile fail.**

- [ ] **Step 3: Implement `CompareToMetaTool`**

```java
package dev.benjamin.wow.raiderio.tools;

import dev.benjamin.wow.raiderio.client.RaiderioClient;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import dev.benjamin.wow.raiderio.client.dto.MythicPlusLeaderboardResponse;
import dev.benjamin.wow.raiderio.tools.format.TextFormatter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    @Tool(name = "wow_compare_to_meta",
          description = "Compare le build actuel d'un personnage (talents, stats) avec la meta courante des top players sur Raider.io.")
    public String wow_compare_to_meta(
            @ToolParam(description = "Character name") String name,
            @ToolParam(description = "Realm slug") String realm,
            @ToolParam(description = "Region: eu, us, kr, tw") String region) {

        String reg = (region == null || region.isBlank()) ? "eu" : region.toLowerCase();
        CharacterProfile p = client.fetchCharacterProfile(reg, realm, name, "talents,gear");
        MythicPlusLeaderboardResponse lb = client.fetchLeaderboardRuns("world", currentSeason, "all", 0);

        TextFormatter f = new TextFormatter();
        f.section(p.name() + " vs meta");
        f.line("Spec: " + p.activeSpecName() + " " + p.characterClass());

        long totalRosterSlots = Optional.ofNullable(lb.rankings()).orElse(java.util.List.of()).stream()
            .flatMap(r -> Optional.ofNullable(r.run().roster()).orElse(java.util.List.of()).stream())
            .count();
        long specAppearances = Optional.ofNullable(lb.rankings()).orElse(java.util.List.of()).stream()
            .flatMap(r -> Optional.ofNullable(r.run().roster()).orElse(java.util.List.of()).stream())
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
        } else f.line("No talent loadout returned.");

        f.section("Note");
        f.line("Per-talent comparison requires decoding the loadout string (Wowhead / Icy Veins). This tool reports spec representation only.");
        return f.toString();
    }
}
```

- [ ] **Step 4: Run — expect PASS.**

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat(tool): add wow_compare_to_meta"
```

---

### Task 11: Register all tools as a single ToolCallbackProvider + smoke boot test

**Files:**
- Modify: `src/main/java/dev/benjamin/wow/raiderio/RaiderioMcpApplication.java`
- Test: `src/test/java/dev/benjamin/wow/raiderio/McpServerSmokeTest.java`

- [ ] **Step 1: Write failing test**

```java
package dev.benjamin.wow.raiderio;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = { "spring.main.web-application-type=none" })
class McpServerSmokeTest {
    @Autowired ToolCallbackProvider provider;

    @Test
    void allFiveToolsAreRegistered() {
        var names = java.util.Arrays.stream(provider.getToolCallbacks())
            .map(cb -> cb.getToolDefinition().name()).toList();
        assertThat(names).contains(
            "wow_character_audit", "wow_evaluate_player", "wow_weekly_plan",
            "wow_find_upgrades", "wow_compare_to_meta");
    }
}
```

- [ ] **Step 2: Run — expect failure (ToolCallbackProvider missing or returns empty).**

- [ ] **Step 3: Update `RaiderioMcpApplication`**

```java
package dev.benjamin.wow.raiderio;

import dev.benjamin.wow.raiderio.tools.*;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableCaching
public class RaiderioMcpApplication {
    public static void main(String[] args) { SpringApplication.run(RaiderioMcpApplication.class, args); }

    @Bean
    public ToolCallbackProvider wowTools(
            CharacterAuditTool t1, EvaluatePlayerTool t2, WeeklyPlanTool t3,
            FindUpgradesTool t4, CompareToMetaTool t5) {
        return MethodToolCallbackProvider.builder().toolObjects(t1, t2, t3, t4, t5).build();
    }
}
```

- [ ] **Step 4: Run — expect PASS.** `mvn -q test -Dtest=McpServerSmokeTest`

- [ ] **Step 5: Run full suite** `mvn -q test`

Expected: all tests green.

- [ ] **Step 6: Verify the jar builds and starts**

```bash
mvn -q clean package -DskipTests
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | java -jar target/wow-raiderio-mcp.jar
```

Expected: JSON response on stdout containing the 5 tool definitions. No Spring banner, no INFO logs polluting stdout.

- [ ] **Step 7: Commit**

```bash
git add .
git commit -m "feat: register all 5 tools via MethodToolCallbackProvider + smoke test"
```

---

### Task 12: README with Claude Code / Claude Desktop integration

**Files:**
- Create: `README.md`

- [ ] **Step 1: Write README with these sections**

- Title + 1-line description
- Prerequisites (Java 21, Maven)
- Build: `mvn clean package`
- The 5 tools (name, description, params)
- Claude Code integration (`.mcp.json` in your project root):

```json
{
  "mcpServers": {
    "wow-raiderio": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/wow-raiderio-mcp/target/wow-raiderio-mcp.jar"]
    }
  }
}
```

- Claude Desktop integration (`~/Library/Application Support/Claude/claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "wow-raiderio": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/wow-raiderio-mcp/target/wow-raiderio-mcp.jar"]
    }
  }
}
```

- Sample prompts to try
- Troubleshooting note: if the server errors out silently, check stderr. If tools don't show up, ensure stdout is clean (no banner, no logs).

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add README with Claude Code / Claude Desktop setup"
```

---

## Self-Review

**Spec coverage check:**
- wow_character_audit → Task 6 ✓
- wow_evaluate_player → Task 7 ✓
- wow_weekly_plan → Task 8 ✓
- wow_find_upgrades → Task 9 ✓
- wow_compare_to_meta → Task 10 ✓
- WebClient + Caffeine + 3 TTLs → Tasks 3 + 4 ✓
- STDIO / stderr-only logs / no banner → Task 1 ✓
- WireMock tests with realistic fixture → Tasks 3, 6–11 ✓
- snake_case `wow_` prefix on tool names → Tasks 6–10 ✓
- Build `mvn clean package` + run `java -jar …` → Task 11 Step 6 ✓
- README with claude_desktop_config.json + .mcp.json → Task 12 ✓

**Placeholder scan:** no "TBD", no "add appropriate error handling", no "similar to task N". All steps contain runnable content.

**Type consistency:** DTO record names and accessor method names used in tools match Task 2 definitions (e.g. `characterClass()`, `activeSpecName()`, `itemLevelEquipped()`, `isTimed()`).

**Edge cases covered:**
- 404 character not found → Task 3 test `throwsOnCharacterNotFound`
- Character with no runs → Tools check `Optional.ofNullable(...).orElse(List.of())` before iterating
- No gear data → `FindUpgradesTool` early-returns with message
- Caching validated → `CacheConfigTest.secondCallHitsCacheAndDoesNotReRequest`
