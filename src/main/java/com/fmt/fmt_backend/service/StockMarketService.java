package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.IndexData;
import com.fmt.fmt_backend.dto.StockData;
import com.fmt.fmt_backend.dto.MarketOverview;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockMarketService {

    private final NseClientService nseClientService;

    private static final int DEFAULT_LIMIT = 10;

    // Market hours in IST
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    /**
     * Get top 10 gainers - cached for 30 minutes
     */
    @Cacheable(value = "topGainers", key = "#limit")
    public List<StockData> getTopGainers(int limit) {
        log.info("Fetching top {} gainers from NSE", limit);
        return nseClientService.fetchTopGainers(limit);
    }

    /**
     * Get top 10 gainers with default limit
     */
    public List<StockData> getTopGainers() {
        return getTopGainers(DEFAULT_LIMIT);
    }

    /**
     * Get top 10 losers - cached for 30 minutes
     */
    @Cacheable(value = "topLosers", key = "#limit")
    public List<StockData> getTopLosers(int limit) {
        log.info("Fetching top {} losers from NSE", limit);
        return nseClientService.fetchTopLosers(limit);
    }

    /**
     * Get top 10 losers with default limit
     */
    public List<StockData> getTopLosers() {
        return getTopLosers(DEFAULT_LIMIT);
    }

    /**
     * Get all main indices (NIFTY 50, Bank NIFTY, etc.) - cached for 30 minutes
     */
    @Cacheable(value = "indices")
    public List<IndexData> getIndices() {
        log.info("Fetching main indices from NSE");
        return nseClientService.fetchIndices();
    }

    /**
     * Get specific index data - cached for 30 minutes
     */
    @Cacheable(value = "indexData", key = "#indexName")
    public IndexData getIndexData(String indexName) {
        log.info("Fetching index data for: {}", indexName);
        return nseClientService.fetchIndexData(indexName);
    }

    /**
     * Get complete market overview - this is what your frontend scrolling bar needs
     * Returns top 10 gainers, top 10 losers, and major indices
     */
    @Cacheable(value = "marketOverview")
    public MarketOverview getMarketOverview() {
        log.info("Fetching complete market overview");

        List<StockData> gainers = nseClientService.fetchTopGainers(DEFAULT_LIMIT);
        List<StockData> losers = nseClientService.fetchTopLosers(DEFAULT_LIMIT);
        List<IndexData> indices = nseClientService.fetchIndices();

        return MarketOverview.builder()
                .topGainers(gainers)
                .topLosers(losers)
                .indices(indices)
                .lastUpdated(LocalDateTime.now())
                .marketStatus(getMarketStatus())
                .build();
    }

    /**
     * Get current market status
     */
    public String getMarketStatus() {
        LocalTime now = LocalTime.now();

        if (now.isBefore(MARKET_OPEN)) {
            return "PRE_MARKET";
        } else if (now.isAfter(MARKET_CLOSE)) {
            return "CLOSED";
        } else {
            return "OPEN";
        }
    }

    /**
     * Clear all caches - scheduled to run every 30 minutes during market hours
     * This ensures fresh data is fetched periodically
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes = 1800000 ms
    @CacheEvict(value = {"topGainers", "topLosers", "indices", "indexData", "marketOverview"}, allEntries = true)
    public void refreshCache() {
        log.info("Cache cleared - will fetch fresh data on next request");
    }

    /**
     * Manual cache refresh - can be triggered via admin endpoint
     */
    @CacheEvict(value = {"topGainers", "topLosers", "indices", "indexData", "marketOverview"}, allEntries = true)
    public void forceRefreshCache() {
        log.info("Manual cache refresh triggered");
    }
}
