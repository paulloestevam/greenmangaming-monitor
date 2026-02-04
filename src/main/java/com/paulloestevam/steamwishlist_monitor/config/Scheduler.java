package com.paulloestevam.steamwishlist_monitor.config;

import com.paulloestevam.steamwishlist_monitor.service.SteamService;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Scheduler {

    private final SteamService service;

    public Scheduler(SteamService service) {
        this.service = service;
    }

    @PostConstruct
    public void init() {
        fetchDealsPeriodically();
    }

    @Scheduled(cron = "${scheduler.time}")
    public void fetchDealsPeriodically() {
        service.fetchDeals();
    }


}