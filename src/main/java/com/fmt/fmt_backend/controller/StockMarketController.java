package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.dto.IndexData;
import com.fmt.fmt_backend.dto.MarketOverview;
import com.fmt.fmt_backend.dto.StockData;
import com.fmt.fmt_backend.service.StockMarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Stock Market", description = "APIs for NSE/BSE stock market data - Top Gainers, Losers & Indices")
@CrossOrigin(origins = "*")
public class StockMarketController {

    private final StockMarketService stockMarketService;

    /**
     * Get complete market overview - ideal for scrolling ticker
     */
    @GetMapping("/overview")
    @Operation(
            summary = "Get Market Overview",
            description = "Returns top 10 gainers, top 10 losers, and major indices (NIFTY 50, Bank NIFTY, etc.). " +
                    "Data is cached and refreshed every 30 minutes. Perfect for the scrolling ticker on your LMS frontend."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Market overview fetched successfully",
                    content = @Content(schema = @Schema(implementation = MarketOverview.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error or NSE unavailable"
            )
    })
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<MarketOverview>> getMarketOverview() {
        log.info("Request received for market overview");

        try {
            MarketOverview overview = stockMarketService.getMarketOverview();
            return ResponseEntity.ok(ApiResponse.success("Market overview fetched successfully", overview));
        } catch (Exception e) {
            log.error("Error fetching market overview: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch market data: " + e.getMessage()));
        }
    }

    /**
     * Get top gainers
     */
    @GetMapping("/gainers")
    @Operation(
            summary = "Get Top Gainers",
            description = "Returns top N gaining stocks from NSE NIFTY. Default limit is 10."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Top gainers fetched successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<List<StockData>>> getTopGainers(
            @Parameter(description = "Number of stocks to return (max 20)")
            @RequestParam(defaultValue = "10") int limit
    ) {
        log.info("Request received for top {} gainers", limit);

        try {
            int cappedLimit = Math.min(limit, 20);
            List<StockData> gainers = stockMarketService.getTopGainers(cappedLimit);
            return ResponseEntity.ok(ApiResponse.success("Top gainers fetched successfully", gainers));
        } catch (Exception e) {
            log.error("Error fetching top gainers: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch top gainers: " + e.getMessage()));
        }
    }

    /**
     * Get top losers
     */
    @GetMapping("/losers")
    @Operation(
            summary = "Get Top Losers",
            description = "Returns top N losing stocks from NSE NIFTY. Default limit is 10."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Top losers fetched successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<List<StockData>>> getTopLosers(
            @Parameter(description = "Number of stocks to return (max 20)")
            @RequestParam(defaultValue = "10") int limit
    ) {
        log.info("Request received for top {} losers", limit);

        try {
            int cappedLimit = Math.min(limit, 20);
            List<StockData> losers = stockMarketService.getTopLosers(cappedLimit);
            return ResponseEntity.ok(ApiResponse.success("Top losers fetched successfully", losers));
        } catch (Exception e) {
            log.error("Error fetching top losers: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch top losers: " + e.getMessage()));
        }
    }

    /**
     * Get all major indices
     */
    @GetMapping("/indices")
    @Operation(
            summary = "Get Major Indices",
            description = "Returns data for major indices including NIFTY 50, NIFTY BANK, NIFTY IT, etc."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Indices fetched successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<List<IndexData>>> getIndices() {
        log.info("Request received for indices");

        try {
            List<IndexData> indices = stockMarketService.getIndices();
            return ResponseEntity.ok(ApiResponse.success("Indices fetched successfully", indices));
        } catch (Exception e) {
            log.error("Error fetching indices: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch indices: " + e.getMessage()));
        }
    }

    /**
     * Get specific index data
     */
    @GetMapping("/index/{indexName}")
    @Operation(
            summary = "Get Index Data",
            description = "Returns data for a specific index. Valid values: NIFTY 50, NIFTY BANK, NIFTY IT, etc."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Index data fetched successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Index not found"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<IndexData>> getIndexData(
            @Parameter(description = "Index name (e.g., 'NIFTY 50', 'NIFTY BANK', 'BANKNIFTY')")
            @PathVariable String indexName
    ) {
        log.info("Request received for index: {}", indexName);

        try {
            IndexData indexData = stockMarketService.getIndexData(indexName);

            if (indexData == null) {
                return ResponseEntity.status(404)
                        .body(ApiResponse.error("Index not found: " + indexName));
            }

            return ResponseEntity.ok(ApiResponse.success("Index data fetched successfully", indexData));
        } catch (Exception e) {
            log.error("Error fetching index data for {}: {}", indexName, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch index data: " + e.getMessage()));
        }
    }

    /**
     * Get market status
     */
    @GetMapping("/status")
    @Operation(
            summary = "Get Market Status",
            description = "Returns current market status: PRE_MARKET, OPEN, or CLOSED"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Market status fetched successfully"
            )
    })
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<String>> getMarketStatus() {
        String status = stockMarketService.getMarketStatus();
        return ResponseEntity.ok(ApiResponse.success("Market status: " + status, status));
    }

    /**
     * Force refresh cache - Admin endpoint
     */
    @PostMapping("/refresh-cache")
    @Operation(
            summary = "Refresh Cache (Admin)",
            description = "Forces a cache refresh. Use sparingly to avoid rate limiting by NSE."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Cache refreshed successfully"
            )
    })
    public ResponseEntity<ApiResponse<String>> refreshCache() {
        log.info("Manual cache refresh requested");
        stockMarketService.forceRefreshCache();
        return ResponseEntity.ok(ApiResponse.success("Cache cleared successfully", "Data will be refreshed on next request"));
    }
}