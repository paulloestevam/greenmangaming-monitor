package com.paulloestevam.steamwishlist_monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SteamWishlistMonitorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SteamWishlistMonitorApplication.class, args);
    }
}
