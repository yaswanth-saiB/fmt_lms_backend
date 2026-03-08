package com.fmt.fmt_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockData {

    private String symbol;              // e.g., "RELIANCE", "TCS"

    private String companyName;         // e.g., "Reliance Industries Ltd"

    private String series;              // e.g., "EQ"

    @JsonProperty("openPrice")
    private BigDecimal openPrice;       // Day's open price

    @JsonProperty("highPrice")
    private BigDecimal highPrice;       // Day's high

    @JsonProperty("lowPrice")
    private BigDecimal lowPrice;        // Day's low

    @JsonProperty("ltp")
    private BigDecimal lastTradedPrice; // Current/Last traded price

    @JsonProperty("previousClose")
    private BigDecimal previousClose;   // Previous day's close

    @JsonProperty("change")
    private BigDecimal change;          // Price change (₹)

    @JsonProperty("percentChange")
    private BigDecimal percentChange;   // % change (+ve for gainers, -ve for losers)

    @JsonProperty("tradedQuantity")
    private Long tradedQuantity;        // Volume traded

    @JsonProperty("turnoverInLakhs")
    private BigDecimal turnoverInLakhs; // Turnover in lakhs (₹)
}