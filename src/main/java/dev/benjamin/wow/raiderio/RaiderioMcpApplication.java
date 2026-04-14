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
