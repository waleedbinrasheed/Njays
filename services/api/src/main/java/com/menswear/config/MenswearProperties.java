package com.menswear.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "menswear")
public record MenswearProperties(
        Cors cors
) {
    public record Cors(List<String> allowedOrigins) {}
}
