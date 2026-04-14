package dev.benjamin.wow.raiderio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "raiderio")
public record RaiderioProperties(String baseUrl, String defaultRegion, int timeoutSeconds) {}
