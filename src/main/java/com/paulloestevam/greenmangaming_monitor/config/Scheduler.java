package com.paulloestevam.greenmangaming_monitor.config;

import com.paulloestevam.greenmangaming_monitor.service.GmgService;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Scheduler {

    private final GmgService gmgService;

    public Scheduler(GmgService gmgService) {
        this.gmgService = gmgService;
    }

    @PostConstruct
    public void init() {
        fetchDealsPeriodically();
    }

    @Scheduled(cron = "${scheduler.time}")
    public void fetchDealsPeriodically() {
        gmgService.fetchDeals();
    }


}