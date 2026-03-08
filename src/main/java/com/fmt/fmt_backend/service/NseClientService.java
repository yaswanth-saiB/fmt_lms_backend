package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.StockData;
import com.fmt.fmt_backend.dto.IndexData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.HttpCookie;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class NseClientService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // NSE India URLs
    private static final String NSE_BASE_URL = "https://www.nseindia.com";
    private static final String NSE_GAINERS_URL = NSE_BASE_URL + "/api/live-analysis-variations?index=gainers";
    private static final String NSE_LOSERS_URL = NSE_BASE_URL + "/api/live-analysis-variations?index=losers";
    private static final String NSE_ALL_INDICES_URL = NSE_BASE_URL + "/api/allIndices";
    private static final String NSE_INDEX_URL = NSE_BASE_URL + "/api/equity-stockIndices?index=";

    // Session management
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private long lastSessionTime = 0;
    private static final long SESSION_VALIDITY_MS = 60000; // 1 minute

    public NseClientService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restTemplate = createRestTemplate();
    }

    private RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate();
        return rt;
    }

    /**
     * Create headers that mimic a real browser
     */
    private HttpHeaders createBrowserHeaders() {
        HttpHeaders headers = new HttpHeaders();

        // Essential headers to bypass Akamai protection
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        headers.set("Accept-Encoding", "gzip, deflate, br");
        headers.set("Connection", "keep-alive");
        headers.set("Cache-Control", "no-cache");
        headers.set("Pragma", "no-cache");
        headers.set("Sec-Ch-Ua", "\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"");
        headers.set("Sec-Ch-Ua-Mobile", "?0");
        headers.set("Sec-Ch-Ua-Platform", "\"Windows\"");
        headers.set("Sec-Fetch-Dest", "document");
        headers.set("Sec-Fetch-Mode", "navigate");
        headers.set("Sec-Fetch-Site", "none");
        headers.set("Sec-Fetch-User", "?1");
        headers.set("Upgrade-Insecure-Requests", "1");

        return headers;
    }

    /**
     * Create headers for API requests (after session is established)
     */
    private HttpHeaders createApiHeaders() {
        HttpHeaders headers = new HttpHeaders();

        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        headers.set("Accept", "application/json, text/plain, */*");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        headers.set("Accept-Encoding", "gzip, deflate, br");
        headers.set("Referer", "https://www.nseindia.com/market-data/live-equity-market");
        headers.set("Sec-Ch-Ua", "\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"");
        headers.set("Sec-Ch-Ua-Mobile", "?0");
        headers.set("Sec-Ch-Ua-Platform", "\"Windows\"");
        headers.set("Sec-Fetch-Dest", "empty");
        headers.set("Sec-Fetch-Mode", "cors");
        headers.set("Sec-Fetch-Site", "same-origin");
        headers.set("Connection", "keep-alive");

        // Add cookies if available
        if (!cookies.isEmpty()) {
            String cookieString = String.join("; ",
                    cookies.entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .toList()
            );
            headers.set("Cookie", cookieString);
        }

        return headers;
    }

    /**
     * Initialize session by visiting the main NSE page
     */
    private synchronized boolean initializeSession() {
        long currentTime = System.currentTimeMillis();

        // Check if session is still valid
        if (!cookies.isEmpty() && (currentTime - lastSessionTime) < SESSION_VALIDITY_MS) {
            return true;
        }

        log.info("Initializing NSE session...");

        try {
            // Step 1: Visit the main page to get initial cookies
            HttpEntity<String> entity = new HttpEntity<>(createBrowserHeaders());

            ResponseEntity<String> response = restTemplate.exchange(
                    NSE_BASE_URL,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            // Extract cookies from response
            extractCookies(response.getHeaders());

            if (!cookies.isEmpty()) {
                lastSessionTime = currentTime;
                log.info("NSE session initialized with {} cookies", cookies.size());

                // Small delay to mimic human behavior
                Thread.sleep(500);
                return true;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Failed to initialize NSE session: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Extract cookies from response headers
     */
    private void extractCookies(HttpHeaders headers) {
        List<String> setCookieHeaders = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookieHeaders != null) {
            for (String cookieHeader : setCookieHeaders) {
                try {
                    // Parse cookie - take only the name=value part
                    String[] parts = cookieHeader.split(";")[0].split("=", 2);
                    if (parts.length == 2) {
                        cookies.put(parts[0].trim(), parts[1].trim());
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse cookie: {}", cookieHeader);
                }
            }
        }
    }

    /**
     * Make API request with retry logic
     */
    private String makeApiRequest(String url) {
        int maxRetries = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Ensure session is initialized
                if (!initializeSession()) {
                    log.warn("Session initialization failed, attempt {}/{}", attempt, maxRetries);
                    cookies.clear();
                    lastSessionTime = 0;
                    Thread.sleep(1000);
                    continue;
                }

                HttpEntity<String> entity = new HttpEntity<>(createApiHeaders());

                ResponseEntity<String> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        String.class
                );

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    // Update cookies if new ones are provided
                    extractCookies(response.getHeaders());
                    return response.getBody();
                }

            } catch (Exception e) {
                log.warn("API request failed (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());

                // Clear session on failure
                cookies.clear();
                lastSessionTime = 0;

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(2000 * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Fetch top gainers from NSE
     */
    public List<StockData> fetchTopGainers(int limit) {
        log.info("Fetching top {} gainers from NSE", limit);

        String response = makeApiRequest(NSE_GAINERS_URL);
        if (response != null) {
            return parseGainersLosersResponse(response, limit);
        }

        log.warn("Failed to fetch gainers, returning empty list");
        return Collections.emptyList();
    }

    /**
     * Fetch top losers from NSE
     */
    public List<StockData> fetchTopLosers(int limit) {
        log.info("Fetching top {} losers from NSE", limit);

        String response = makeApiRequest(NSE_LOSERS_URL);
        if (response != null) {
            return parseGainersLosersResponse(response, limit);
        }

        log.warn("Failed to fetch losers, returning empty list");
        return Collections.emptyList();
    }

    /**
     * Fetch all major indices
     */
    public List<IndexData> fetchIndices() {
        log.info("Fetching indices from NSE");

        String response = makeApiRequest(NSE_ALL_INDICES_URL);
        if (response != null) {
            return parseIndicesResponse(response);
        }

        log.warn("Failed to fetch indices, returning empty list");
        return Collections.emptyList();
    }

    /**
     * Fetch specific index data
     */
    public IndexData fetchIndexData(String indexName) {
        log.info("Fetching index data for: {}", indexName);

        String encodedIndex = indexName.replace(" ", "%20");
        String url = NSE_INDEX_URL + encodedIndex;

        String response = makeApiRequest(url);
        if (response != null) {
            return parseSingleIndexResponse(response);
        }

        return null;
    }

    /**
     * Parse gainers/losers API response
     */
    private List<StockData> parseGainersLosersResponse(String responseBody, int limit) {
        List<StockData> stocks = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Try different possible response structures
            JsonNode dataNode = root.get("NIFTY");
            if (dataNode == null) dataNode = root.get("data");
            if (dataNode == null) dataNode = root.get("allSec");
            if (dataNode == null && root.isArray()) dataNode = root;

            if (dataNode != null && dataNode.isArray()) {
                int count = 0;
                for (JsonNode stockNode : dataNode) {
                    if (count >= limit) break;

                    StockData stock = StockData.builder()
                            .symbol(getText(stockNode, "symbol"))
                            .companyName(getText(stockNode, "symbol", "companyName"))
                            .series(getText(stockNode, "series"))
                            .openPrice(getDecimal(stockNode, "open_price", "openPrice", "open"))
                            .highPrice(getDecimal(stockNode, "high_price", "highPrice", "dayHigh"))
                            .lowPrice(getDecimal(stockNode, "low_price", "lowPrice", "dayLow"))
                            .lastTradedPrice(getDecimal(stockNode, "ltp", "lastPrice", "last_price"))
                            .previousClose(getDecimal(stockNode, "prev_price", "previousPrice", "previousClose", "prevClose"))
                            .change(getDecimal(stockNode, "netPrice", "net_price", "change"))
                            .percentChange(getDecimal(stockNode, "perChange", "pChange", "percentChange", "per_change"))
                            .tradedQuantity(getLong(stockNode, "tradedQuantity", "qty", "volume"))
                            .turnoverInLakhs(getDecimal(stockNode, "turnoverInLakhs", "turnover"))
                            .build();

                    if (stock.getSymbol() != null) {
                        stocks.add(stock);
                        count++;
                    }
                }
            }

            log.info("Parsed {} stocks from response", stocks.size());

        } catch (Exception e) {
            log.error("Error parsing gainers/losers response: {}", e.getMessage());
        }

        return stocks;
    }

    /**
     * Parse all indices response
     */
    private List<IndexData> parseIndicesResponse(String responseBody) {
        List<IndexData> indices = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode dataNode = root.get("data");

            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode indexNode : dataNode) {
                    String indexName = getText(indexNode, "index", "indexName");

                    // Filter main indices
                    if (isMainIndex(indexName)) {
                        IndexData indexData = IndexData.builder()
                                .indexName(indexName)
                                .indexSymbol(getText(indexNode, "indexSymbol", "index"))
                                .lastPrice(getDecimal(indexNode, "last", "lastPrice"))
                                .open(getDecimal(indexNode, "open"))
                                .high(getDecimal(indexNode, "high"))
                                .low(getDecimal(indexNode, "low"))
                                .previousClose(getDecimal(indexNode, "previousClose", "prevClose"))
                                .change(getDecimal(indexNode, "variation", "change"))
                                .percentChange(getDecimal(indexNode, "percentChange", "pChange"))
                                .advances(getInt(indexNode, "advances"))
                                .declines(getInt(indexNode, "declines"))
                                .unchanged(getInt(indexNode, "unchanged"))
                                .lastUpdated(LocalDateTime.now())
                                .build();

                        indices.add(indexData);
                    }
                }
            }

            log.info("Parsed {} indices from response", indices.size());

        } catch (Exception e) {
            log.error("Error parsing indices response: {}", e.getMessage());
        }

        return indices;
    }

    /**
     * Parse single index response
     */
    private IndexData parseSingleIndexResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode metadata = root.get("metadata");

            if (metadata != null) {
                return IndexData.builder()
                        .indexName(getText(metadata, "indexName", "index"))
                        .indexSymbol(getText(metadata, "indexName"))
                        .lastPrice(getDecimal(metadata, "last", "lastPrice"))
                        .open(getDecimal(metadata, "open"))
                        .high(getDecimal(metadata, "high"))
                        .low(getDecimal(metadata, "low"))
                        .previousClose(getDecimal(metadata, "previousClose"))
                        .change(getDecimal(metadata, "change"))
                        .percentChange(getDecimal(metadata, "percentChange", "pChange"))
                        .lastUpdated(LocalDateTime.now())
                        .build();
            }
        } catch (Exception e) {
            log.error("Error parsing index response: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Check if index is a main index we want to show
     */
    private boolean isMainIndex(String indexName) {
        if (indexName == null) return false;
        String upper = indexName.toUpperCase();
        return upper.equals("NIFTY 50") ||
                upper.equals("NIFTY BANK") ||
                upper.equals("NIFTY NEXT 50") ||
                upper.equals("NIFTY IT") ||
                upper.equals("NIFTY FINANCIAL SERVICES") ||
                upper.equals("NIFTY MIDCAP 50") ||
                upper.equals("NIFTY AUTO") ||
                upper.equals("NIFTY PHARMA");
    }

    // ========== JSON Helper Methods ==========

    private String getText(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode fieldNode = node.get(field);
            if (fieldNode != null && !fieldNode.isNull() && fieldNode.isTextual()) {
                return fieldNode.asText();
            }
        }
        return null;
    }

    private BigDecimal getDecimal(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode fieldNode = node.get(field);
            if (fieldNode != null && !fieldNode.isNull()) {
                try {
                    if (fieldNode.isNumber()) {
                        return BigDecimal.valueOf(fieldNode.asDouble());
                    } else if (fieldNode.isTextual()) {
                        String value = fieldNode.asText().replaceAll("[,\\s]", "");
                        if (!value.isEmpty() && !value.equals("-")) {
                            return new BigDecimal(value);
                        }
                    }
                } catch (Exception e) {
                    // Continue to next field
                }
            }
        }
        return null;
    }

    private Integer getInt(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode != null && !fieldNode.isNull()) {
            return fieldNode.asInt();
        }
        return null;
    }

    private Long getLong(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode fieldNode = node.get(field);
            if (fieldNode != null && !fieldNode.isNull()) {
                return fieldNode.asLong();
            }
        }
        return null;
    }
}