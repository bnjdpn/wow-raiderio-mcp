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
            caffeine("affixes",          Duration.ofHours(1),   4),
            caffeine("leaderboard",      Duration.ofMinutes(15), 200)
        ));
        return mgr;
    }

    private CaffeineCache caffeine(String name, Duration ttl, int maxSize) {
        return new CaffeineCache(name,
            Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build());
    }
}
