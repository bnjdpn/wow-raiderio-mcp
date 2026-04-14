package dev.benjamin.wow.raiderio.client;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.benjamin.wow.raiderio.client.dto.AffixesResponse;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import dev.benjamin.wow.raiderio.client.dto.MythicPlusLeaderboardResponse;
import dev.benjamin.wow.raiderio.config.RaiderioProperties;
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
        return new RaiderioClient(webClient, new RaiderioProperties(wm.baseUrl(), "eu", 10));
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

        assertThat(p.name()).isEqualTo("Stormrage");
        assertThat(p.gear().items()).isNotEmpty();
    }

    @Test
    void throwsOnCharacterNotFound() {
        wm.stubFor(get(urlPathEqualTo("/characters/profile"))
            .willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"statusCode\":400,\"error\":\"Bad Request\",\"message\":\"Could not find requested character\"}")));

        assertThatThrownBy(() -> client().fetchCharacterProfile("eu", "x", "nope", "gear"))
            .isInstanceOf(RaiderioException.class)
            .hasMessageContaining("Could not find");
    }

    @Test
    void fetchesWeeklyAffixes() {
        wm.stubFor(get(urlPathEqualTo("/mythic-plus/affixes"))
            .withQueryParam("region", equalTo("eu"))
            .withQueryParam("locale", equalTo("en"))
            .willReturn(okJson("{\"region\":\"eu\",\"title\":\"Fortified, Bolstering, Raging\",\"leaderboard_url\":\"x\",\"affix_details\":[{\"id\":10,\"name\":\"Fortified\",\"description\":\"...\"}]}")));

        AffixesResponse a = client().fetchAffixes("eu", "en");
        assertThat(a.affixDetails()).hasSize(1);
        assertThat(a.title()).contains("Fortified");
    }

    @Test
    void fetchesLeaderboardRuns() {
        wm.stubFor(get(urlPathEqualTo("/mythic-plus/runs"))
            .withQueryParam("region", equalTo("world"))
            .withQueryParam("season", equalTo("season-tww-3"))
            .withQueryParam("dungeon", equalTo("all"))
            .withQueryParam("page", equalTo("0"))
            .willReturn(okJson("{\"rankings\":[{\"rank\":1,\"run\":{\"dungeon\":{\"id\":1,\"name\":\"Ara-Kara\",\"short_name\":\"ARAK\",\"slug\":\"arak\"},\"mythic_level\":20,\"score\":500.0,\"clear_time_ms\":1700000,\"roster\":[]}}]}")));

        MythicPlusLeaderboardResponse r = client().fetchLeaderboardRuns("world", "season-tww-3", "all", 0);
        assertThat(r.rankings()).hasSize(1);
        assertThat(r.rankings().get(0).run().dungeon().shortName()).isEqualTo("ARAK");
    }

    @Test
    void wrapsTimeoutAsRaiderioException() {
        wm.stubFor(get(urlPathEqualTo("/characters/profile"))
            .willReturn(aResponse().withStatus(500).withBody("boom")));

        assertThatThrownBy(() -> client().fetchCharacterProfile("eu", "r", "n", "gear"))
            .isInstanceOf(RaiderioException.class);
    }
}
