# wow-raiderio-mcp

A Spring Boot MCP (Model Context Protocol) server that turns Claude into a World of Warcraft gameplay co-pilot, backed by the [Raider.io](https://raider.io) public API.

Exposes **5 intent-based tools** (not 1:1 API wrappers) over STDIO, usable from Claude Code, Claude Desktop, or any MCP client.

Built for **WoW: Midnight Season 1** (launched March 24, 2026), but works with any current season — the Raider.io season slug is configurable.

## Why this exists

I'm an EU player ([Brennt-Dalaran](https://raider.io/characters/eu/dalaran/Brennt)), and I got tired of tabbing between Raider.io, Wowhead, and my Great Vault tracker between pulls. Asking Claude *"audit Brennt-Dalaran"* in plain English and getting a digested answer is faster than three clicks — and way faster than parsing the site by eye on mobile. This server is what makes that possible.

## What you get

| Tool | What it does |
|------|--------------|
| `wow_character_audit` | Complete audit: M+ score, gear, missing enchants/gems, best & weakest dungeons, recent timed ratio. |
| `wow_evaluate_player` | Quick "is this player a good group invite?" verdict with score, consistency, optional dungeon experience, class utilities. |
| `wow_weekly_plan` | Weekly plan: current affixes, priority dungeons (biggest score-gain potential), Great Vault progression (1/4/8 runs). |
| `wow_find_upgrades` | Bottom 5 gear slots by item level with source hints (M+ / raid / craft). |
| `wow_compare_to_meta` | Spec representation in the current season's top leaderboard runs + your opaque talent loadout string. |

All tools take `name`, `realm`, `region` (default `eu`). `wow_evaluate_player` also takes an optional `dungeon`.

## Midnight Season 1 context

The server ships with `current-season: season-mn-1` as the default season slug in `application.yml`. Midnight S1 brought:

- **8 dungeons** in the pool — 4 brand-new Midnight dungeons (**Magisters' Terrace**, **Maisara Caverns**, **Nexus-Point Xenas**, **Windrunner Spire**) + 4 returning legacy dungeons.
- **Lindormi's Guidance** — a new onboarding affix that runs on +2 through +5 keys, highlighting an optimal route and suppressing the death-timer penalty. Great for newer M+ players, irrelevant at high keys.
- **Xal'atath's Bargain** family — returning from TWW as the mid-to-high key affix rotation.

If Raider.io's season slug changes (e.g. `season-mn-1-post-110x`), override via `RAIDERIO_CURRENT_SEASON` or in `application.yml`.

## Requirements

- **Java 25** (Temurin). The project targets `--release 25`.
- **Maven 3.9+**.

## Install

### 1. Build the jar

```bash
git clone https://github.com/bnjdpn/wow-raiderio-mcp.git
cd wow-raiderio-mcp
mvn clean package
```

Produces `target/wow-raiderio-mcp.jar` (runnable Spring Boot fat jar, ~43 MB).

### 2. Wire it into your MCP client

**Claude Code** — add to `.mcp.json` in your project (or `~/.claude.json` global):

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

Restart Claude Code. Tools will appear as `mcp__wow-raiderio__wow_*`.

**Claude Desktop** — edit `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) or `%APPDATA%\Claude\claude_desktop_config.json` (Windows):

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

Restart the app.

### 3. Smoke-test (optional)

Drive the server manually over STDIO:

```bash
(
  echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"cli","version":"1.0"}}}'
  echo '{"jsonrpc":"2.0","method":"notifications/initialized"}'
  echo '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
  sleep 2
) | java -jar target/wow-raiderio-mcp.jar
```

You should see all 5 tools in the response.

## Sample prompts

Try these in Claude:

- *"Audit Brennt on Dalaran EU."*
- *"Is Brennt-Dalaran-EU a good pick for a +14 Maisara Caverns key this week?"*
- *"Plan my week for Brennt-Dalaran-EU."*
- *"What gear should Brennt-Dalaran-EU upgrade first?"*
- *"How does Brennt-Dalaran-EU's build compare to the Midnight S1 meta?"*

Claude will invoke the matching tool and give you a digested answer — no raw JSON.

## Configuration

Full defaults in `src/main/resources/application.yml`:

```yaml
raiderio:
  base-url: https://raider.io/api/v1
  default-region: eu
  timeout-seconds: 10
  current-season: season-mn-1
```

Environment-variable overrides (Spring relaxed binding):

- `RAIDERIO_BASE_URL`
- `RAIDERIO_DEFAULT_REGION`
- `RAIDERIO_TIMEOUT_SECONDS`
- `RAIDERIO_CURRENT_SEASON`

## Caching

Caffeine, three named caches:

- `characterProfile` — 5 min, 500 entries
- `affixes` — 1 hour, 4 entries
- `leaderboard` — 15 min, 200 entries

Tuned to be kind to the [Raider.io](https://raider.io) API while keeping data fresh enough for a gameplay session.

## Architecture

```
RaiderioMcpApplication       (@SpringBootApplication, registers ToolCallbackProvider)
 └── tools/                   5 @Component + @Tool classes
 └── client/                  RaiderioClient (WebClient + @Cacheable)
 └── client/dto/              Jackson records for Raider.io responses
 └── config/                  WebClient, Caffeine cache, @ConfigurationProperties
 └── tools/format/            TextFormatter helper
```

Transport is **STDIO** — no HTTP server starts (`spring.main.web-application-type=none`). All logs go to **stderr** (`logback-spring.xml`) so stdout stays a clean JSON-RPC channel — mandatory for STDIO-mode MCP.

## Tests

```bash
mvn test
```

42 tests: DTO deserialization, WireMock-based HTTP client, cache behavior, per-tool unit tests against realistic fixtures, plus an end-to-end `@SpringBootTest` verifying all 5 tools are registered with Spring AI's `ToolCallbackProvider`.

## Troubleshooting

- **Claude says "tool not found"** — absolute path in the config, then restart your client.
- **`release version 25 not supported` when building** — install Temurin 25 and set `JAVA_HOME`.
- **No output from the STDIO smoke test** — inspect stderr. Any log on stdout corrupts MCP.
- **Character not found (HTTP 400)** — realm slug must be lowercase with dashes (`argent-dawn`, not `Argent Dawn`); region is one of `eu`, `us`, `kr`, `tw`.
- **`wow_compare_to_meta` returns 0/0** — Raider.io probably renamed the season slug. Set `RAIDERIO_CURRENT_SEASON` to the new value (check a URL on raider.io — the `?season=` query param shows the current slug).

## Contributing

PRs welcome. Guidelines:

1. **Fork + branch** — `feat/...`, `fix/...`, `docs/...`.
2. **TDD** — every tool and every client method has WireMock or Mockito coverage. A PR without tests will be asked to add them.
3. **Intent-based tools only** — if you're adding a tool that's a 1:1 wrapper over a Raider.io endpoint, it probably doesn't belong here. Think "what question would a player ask Claude?" and design the tool around that.
4. **Keep stdout clean** — never add `System.out.println` or routes logs to stdout. The STDIO protocol is unforgiving.
5. **Run the full suite** before opening the PR:
   ```bash
   mvn test
   ```
6. **One tool per PR** if you're adding multiple — easier to review and revert.

Open an issue first if you're planning anything that touches multiple layers (new DTO + client method + tool).

## Credits

- **[Raider.io](https://raider.io)** ([@raiderio](https://github.com/raiderio)) for running a free, well-documented public API and for cultivating the Mythic+ scene. All the data this server serves up comes from them; please don't hammer their API — the Caffeine caches here exist for a reason.
- **Spring AI** team for the MCP Server starter.
- **Anthropic** for making MCP a real standard.

## Stack

Java 25 · Spring Boot 4.0.5 · Spring AI 2.0.0-M3 (MCP Server starter, STDIO transport) · Jackson 3 · WebFlux `WebClient` · Caffeine · JUnit 5 · WireMock · AssertJ · Mockito.
