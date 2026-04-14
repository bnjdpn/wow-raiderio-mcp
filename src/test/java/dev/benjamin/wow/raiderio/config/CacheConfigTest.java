package dev.benjamin.wow.raiderio.config;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.benjamin.wow.raiderio.client.RaiderioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CacheConfigTest.TestApp.class,
    properties = { "spring.main.web-application-type=none" })
class CacheConfigTest {

    @Configuration
    @EnableCaching
    @Import(CacheConfig.class)
    static class TestApp {
        @Bean
        RaiderioProperties raiderioProperties(
                @org.springframework.beans.factory.annotation.Value("${raiderio.base-url}") String baseUrl) {
            return new RaiderioProperties(baseUrl, "eu", 10);
        }

        @Bean
        WebClient raiderioWebClient(RaiderioProperties props) {
            return WebClient.builder().baseUrl(props.baseUrl()).build();
        }

        @Bean
        RaiderioClient raiderioClient(WebClient raiderioWebClient, RaiderioProperties props) {
            return new RaiderioClient(raiderioWebClient, props);
        }
    }

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("raiderio.base-url", wm::baseUrl);
    }

    @Autowired RaiderioClient client;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(n -> cacheManager.getCache(n).clear());
        wm.resetAll();
    }

    @Test
    void threeNamedCachesAreRegistered() {
        assertThat(cacheManager.getCacheNames())
            .contains("characterProfile", "affixes", "leaderboard");
    }

    @Test
    void profileCallIsCachedOnSecondInvocation() throws Exception {
        String body = Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json"));
        wm.stubFor(get(urlPathEqualTo("/characters/profile")).willReturn(okJson(body)));

        client.fetchCharacterProfile("eu", "hyjal", "Stormrage", "gear");
        client.fetchCharacterProfile("eu", "hyjal", "Stormrage", "gear");

        wm.verify(1, getRequestedFor(urlPathEqualTo("/characters/profile")));
    }

    @Test
    void differentKeysDoNotCollide() throws Exception {
        String body = Files.readString(Path.of("src/test/resources/fixtures/character-fury-warrior.json"));
        wm.stubFor(get(urlPathEqualTo("/characters/profile")).willReturn(okJson(body)));

        client.fetchCharacterProfile("eu", "hyjal", "Stormrage", "gear");
        client.fetchCharacterProfile("eu", "hyjal", "Stormrage", "gear,talents");
        client.fetchCharacterProfile("us", "hyjal", "Stormrage", "gear");

        wm.verify(3, getRequestedFor(urlPathEqualTo("/characters/profile")));
    }

    @Test
    void affixesCacheSeparateFromLeaderboard() {
        wm.stubFor(get(urlPathEqualTo("/mythic-plus/affixes"))
            .willReturn(okJson("{\"region\":\"eu\",\"title\":\"x\",\"leaderboard_url\":\"x\",\"affix_details\":[]}")));
        wm.stubFor(get(urlPathEqualTo("/mythic-plus/runs"))
            .willReturn(okJson("{\"rankings\":[]}")));

        client.fetchAffixes("eu", "en");
        client.fetchAffixes("eu", "en");
        client.fetchLeaderboardRuns("world", "season-tww-3", "all", 0);
        client.fetchLeaderboardRuns("world", "season-tww-3", "all", 0);

        wm.verify(1, getRequestedFor(urlPathEqualTo("/mythic-plus/affixes")));
        wm.verify(1, getRequestedFor(urlPathEqualTo("/mythic-plus/runs")));
    }
}
