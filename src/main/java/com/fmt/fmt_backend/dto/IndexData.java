package com.fmt.fmt_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexData {

    private String indexName;           // e.g., "NIFTY 50", "NIFTY BANK"

    private String indexSymbol;         // e.g., "NIFTY50", "BANKNIFTY"

    @JsonProperty("lastPrice")
    private BigDecimal lastPrice;       // Current index value

    @JsonProperty("open")
    private BigDecimal open;            // Day's open

    @JsonProperty("high")
    private BigDecimal high;            // Day's high

    @JsonProperty("low")
    private BigDecimal low;             // Day's low

    @JsonProperty("previousClose")
    private BigDecimal previousClose;   // Previous close

    @JsonProperty("change")
    private BigDecimal change;          // Points change

    @JsonProperty("percentChange")
    private BigDecimal percentChange;   // % change

    private Integer advances;           // Stocks advancing in this index

    private Integer declines;           // Stocks declining

    private Integer unchanged;          // Stocks unchanged

    private LocalDateTime lastUpdated;  // When data was fetched
}