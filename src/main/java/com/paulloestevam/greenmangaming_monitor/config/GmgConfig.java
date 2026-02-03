package com.paulloestevam.greenmangaming_monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "gmg")
@Data
public class GmgConfig {
    private List<String> urls;
}