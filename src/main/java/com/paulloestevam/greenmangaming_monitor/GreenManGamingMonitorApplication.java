package com.paulloestevam.greenmangaming_monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GreenManGamingMonitorApplication {
    public static void main(String[] args) {
        SpringApplication.run(GreenManGamingMonitorApplication.class, args);
    }
}
