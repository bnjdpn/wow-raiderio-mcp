# wow-raiderio-mcp

A Spring Boot MCP (Model Context Protocol) server that turns Claude into a World of Warcraft gameplay co-pilot, backed by the [Raider.io](https://raider.io) public API.

Exposes **5 intent-based tools** (not 1:1 API wrappers) over STDIO, usable from Claude Code, Claude Desktop, or any MCP client.

## Tools

| Tool | What it does |
|------|--------------|
| `wow_character_audit` | Complete audit: M+ score, gear, missing enchants/gems, best & weakest dungeons, recent timed ratio. |
| `wow_evaluate_player` | Quick "is this player a good group invite?" verdict with score, consistency, optional dungeon experience, class utilities. |
| `wow_weekly_plan` | Weekly plan: current affixes, priority dungeons (biggest score-gain potential), Great Vault progression (1/4/8 runs). |
| `wow_find_upgrades` | Bottom 5 gear slots by item level with source hints (M+ / raid / craft). |
| `wow_compare_to_meta` | Spec representation in the current season's top leaderboard runs + your opaque talent loadout string. |

All five tools accept `name`, `realm`, `region` (default `eu`). `wow_evaluate_player` also accepts an optional `dungeon`.

## Requirements

- **Java 25** (Temurin). Project targets `--release 25`.
- **Maven 3.9+**.

## Build

```bash
mvn clean package
```

Produces `target/wow-raiderio-mcp.jar` (runnable Spring Boot fat jar, ~43 MB).

## Run (standalone STDIO smoke test)

The server speaks JSON-RPC 2.0 over stdin/stdout. You can drive it manually:

```bash
(
  echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"cli","version":"1.0"}}}'
  echo '{"jsonrpc":"2.0","method":"notifications/initialized"}'
  echo '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
  sleep 2
) | java -jar target/wow-raiderio-mcp.jar
```

You should see the 5 tools in the response.

## Claude Code integration

Add to `.mcp.json` at the root of your project (or `~/.claude.json` for a global config):

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

Restart Claude Code. The tools will appear as `mcp__wow-raiderio__wow_*`.

## Claude Desktop integration

Edit `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) or `%APPDATA%\Claude\claude_desktop_config.json` (Windows):

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

Restart Claude Desktop.

## Sample prompts

- "Audit Stormrage on Hyjal EU."
- "Is Stormrage-Hyjal-EU a good pick for a +14 Ara-Kara?"
- "Plan my week for Stormrage-Hyjal-EU."
- "What gear should Stormrage-Hyjal-EU upgrade first?"
- "How does Stormrage-Hyjal-EU's Fury build compare to meta?"

Claude will invoke the matching tool and present a digest.

## Configuration

All settings in `src/main/resources/application.yml`:

```yaml
raiderio:
  base-url: https://raider.io/api/v1
  default-region: eu
  timeout-seconds: 10
  current-season: season-tww-3      # used by wow_compare_to_meta
```

Override with environment variables using Spring's relaxed binding:
- `RAIDERIO_BASE_URL`, `RAIDERIO_DEFAULT_REGION`, `RAIDERIO_TIMEOUT_SECONDS`, `RAIDERIO_CURRENT_SEASON`

## Caching

Caffeine with 3 named caches:
- `characterProfile` — 5 minutes, 500 entries
- `affixes` — 1 hour, 4 entries
- `leaderboard` — 15 minutes, 200 entries

## Architecture

```
RaiderioMcpApplication (@SpringBootApplication, registers ToolCallbackProvider)
 └── tools/         (@Component + @Tool — 5 intent-based tools)
 └── client/        RaiderioClient (WebClient + @Cacheable)
 └── client/dto/    Jackson records for Raider.io responses
 └── config/        WebClient, Caffeine cache, @ConfigurationProperties
 └── tools/format/  TextFormatter helper
```

Transport is **STDIO** — no HTTP server starts (`spring.main.web-application-type=none`). All logs go to **stderr** (`logback-spring.xml`) so stdout stays a clean JSON-RPC channel.

## Tests

```bash
mvn test
```

42 tests total: DTO deserialization, WireMock-based HTTP client, cache behavior, each tool unit-tested against fixtures, plus an end-to-end `@SpringBootTest` that verifies all 5 tools are registered with Spring AI's `ToolCallbackProvider`.

## Troubleshooting

- **Claude says "tool not found"** — ensure the jar path in your config is absolute, and restart your Claude client.
- **"release version 25 not supported"** when building — install Temurin 25 and set `JAVA_HOME`.
- **No output from the STDIO smoke test** — check stderr for stack traces. Any log on stdout corrupts the MCP protocol.
- **Character not found** — double-check realm slug (lowercase, dashes, e.g. `argent-dawn`) and region (`eu`, `us`, `kr`, `tw`).

## Stack

- Java 25 · Spring Boot 4.0.5 · Spring AI 2.0.0-M3 (MCP Server starter, STDIO transport) · Jackson 3 · WebFlux `WebClient` · Caffeine · JUnit 5 · WireMock · AssertJ · Mockito.
