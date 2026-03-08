package com.fmt.fmt_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketOverview {

    private List<StockData> topGainers;

    private List<StockData> topLosers;

    private List<IndexData> indices;

    private LocalDateTime lastUpdated;

    private String marketStatus;
}