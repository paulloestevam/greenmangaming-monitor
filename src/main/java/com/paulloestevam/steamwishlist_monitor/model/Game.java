package com.paulloestevam.steamwishlist_monitor.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Game {
    private String title;
    private String currentPrice;
    private String originalPrice;
    private int discountPercentage;
    private String url;
    private String imageUrl;
}