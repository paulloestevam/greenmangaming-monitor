package com.paulloestevam.steamwishlist_monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "steam")
@Data
public class SteamWishlistConfig {
    private List<String> urls;
}