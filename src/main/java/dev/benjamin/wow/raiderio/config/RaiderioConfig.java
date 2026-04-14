package dev.benjamin.wow.raiderio.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(RaiderioProperties.class)
public class RaiderioConfig {

    @Bean
    public WebClient raiderioWebClient(RaiderioProperties props) {
        return WebClient.builder()
            .baseUrl(props.baseUrl())
            .defaultHeader("User-Agent", "wow-raiderio-mcp/1.0")
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
    }
}
