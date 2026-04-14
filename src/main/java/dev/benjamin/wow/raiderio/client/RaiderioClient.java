package dev.benjamin.wow.raiderio.client;

import dev.benjamin.wow.raiderio.client.dto.AffixesResponse;
import dev.benjamin.wow.raiderio.client.dto.CharacterProfile;
import dev.benjamin.wow.raiderio.client.dto.MythicPlusLeaderboardResponse;
import dev.benjamin.wow.raiderio.config.RaiderioProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.time.Duration;
import java.util.function.Function;

@Component
public class RaiderioClient {

    private final WebClient webClient;
    private final Duration timeout;

    public RaiderioClient(WebClient raiderioWebClient, RaiderioProperties props) {
        this.webClient = raiderioWebClient;
        this.timeout = Duration.ofSeconds(props.timeoutSeconds());
    }

    public CharacterProfile fetchCharacterProfile(String region, String realm, String name, String fields) {
        return get("/characters/profile", CharacterProfile.class, uri -> uri
            .queryParam("region", region)
            .queryParam("realm", realm)
            .queryParam("name", name)
            .queryParam("fields", fields));
    }

    public AffixesResponse fetchAffixes(String region, String locale) {
        return get("/mythic-plus/affixes", AffixesResponse.class, uri -> uri
            .queryParam("region", region)
            .queryParam("locale", locale));
    }

    public MythicPlusLeaderboardResponse fetchLeaderboardRuns(String region, String season, String dungeon, int page) {
        return get("/mythic-plus/runs", MythicPlusLeaderboardResponse.class, uri -> uri
            .queryParam("region", region)
            .queryParam("season", season)
            .queryParam("dungeon", dungeon)
            .queryParam("page", page));
    }

    private <T> T get(String path, Class<T> type, Function<UriBuilder, UriBuilder> query) {
        try {
            return webClient.get()
                .uri(b -> query.apply(b.path(path)).build())
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
