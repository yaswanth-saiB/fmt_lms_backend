package com.fmt.fmt_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class YahooSessionManager {

    private final RestTemplate restTemplate;

    // ─── NEW — replace the two constants at the top of the class ─────────────────
    private static final String COOKIE_URL = "https://finance.yahoo.com/quote/RELIANCE.NS";
    private static final String CRUMB_URL  = "https://query2.finance.yahoo.com/v1/test/getcrumb";

    // ─── Cached session state ─────────────────────────────────────────────────
    private volatile String cachedCookie     = "";
    private volatile String cachedCrumb      = "";
    private volatile long   sessionFetchedAt = 0;

    // Refresh session every 25 minutes (Yahoo cookies expire ~30 min)
    private static final long SESSION_TTL_MS = 25 * 60 * 1000;

    // ─── Public: get valid crumb (refreshes if expired) ──────────────────────

    public String getCrumb() {
        if (isSessionValid()) return cachedCrumb;
        return refreshSession() ? cachedCrumb : "";
    }

    public String getCookie() {
        if (isSessionValid()) return cachedCookie;
        return refreshSession() ? cachedCookie : "";
    }

    public boolean refreshSession() {
        log.info("🔐 Refreshing Yahoo Finance session (cookie + crumb)...");
        try {
            // Step 1: Get cookie from fc.yahoo.com
            String cookie = fetchCookie();
            if (cookie.isEmpty()) {
                log.error("❌ Failed to fetch Yahoo cookie");
                return false;
            }

            // Step 2: Use cookie to get crumb
            String crumb = fetchCrumb(cookie);
            if (crumb.isEmpty()) {
                log.error("❌ Failed to fetch Yahoo crumb");
                return false;
            }

            this.cachedCookie     = cookie;
            this.cachedCrumb      = crumb;
            this.sessionFetchedAt = System.currentTimeMillis();

            log.info("✅ Yahoo session refreshed. Crumb: {}...", crumb.substring(0, Math.min(5, crumb.length())));
            return true;

        } catch (Exception e) {
            log.error("❌ Yahoo session refresh failed: {}", e.getMessage());
            return false;
        }
    }

    // ─── Step 1: Fetch cookie ─────────────────────────────────────────────────

    private String fetchCookie() {
        try {
            HttpHeaders headers = buildBrowserHeaders(null);
            // Add these extra headers — finance.yahoo.com is stricter than fc.yahoo.com
            headers.set("Upgrade-Insecure-Requests", "1");
            headers.set("Sec-Fetch-Dest",   "document");
            headers.set("Sec-Fetch-Mode",   "navigate");
            headers.set("Sec-Fetch-Site",   "none");
            headers.set("Sec-Fetch-User",   "?1");

            ResponseEntity<String> response = restTemplate.exchange(
                    COOKIE_URL, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );

            List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);

            if (setCookies == null || setCookies.isEmpty()) {
                // finance.yahoo.com sometimes sets cookies via redirect chains
                // Try extracting from response headers directly
                log.warn("⚠️ No Set-Cookie in response headers, trying fallback...");
                return extractCookieFromResponse(response);
            }

            String cookie = String.join("; ", setCookies.stream()
                    .map(c -> c.split(";")[0].trim())
                    .filter(c -> !c.isEmpty())
                    .toList());

            log.info("✅ Cookie fetched successfully ({} cookie entries)", setCookies.size());
            return cookie;

        } catch (Exception e) {
            log.error("Cookie fetch error: {}", e.getMessage());
            return "";
        }
    }

    // Fallback: build a minimal cookie from known Yahoo cookie names
    private String extractCookieFromResponse(ResponseEntity<String> response) {
        // Yahoo sometimes returns A1, A3, A1S, GUC cookies
        // If none found, return a generic browser-like cookie to at least attempt the crumb fetch
        HttpHeaders respHeaders = response.getHeaders();

        List<String> allHeaders = respHeaders.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase("set-cookie"))
                .flatMap(e -> e.getValue().stream())
                .map(c -> c.split(";")[0].trim())
                .filter(c -> !c.isEmpty())
                .toList();

        if (!allHeaders.isEmpty()) {
            return String.join("; ", allHeaders);
        }

        log.warn("⚠️ Could not extract any cookie — crumb fetch may fail");
        return "";
    }

    // ─── Step 2: Fetch crumb using cookie ─────────────────────────────────────

    private String fetchCrumb(String cookie) {
        try {
            HttpHeaders headers = buildBrowserHeaders(cookie);
            ResponseEntity<String> response = restTemplate.exchange(
                    CRUMB_URL, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );

            String crumb = response.getBody();
            if (crumb == null || crumb.isBlank() || crumb.contains("Unauthorized")) {
                log.warn("Invalid crumb received: {}", crumb);
                return "";
            }
            return crumb.trim();

        } catch (Exception e) {
            log.error("Crumb fetch error: {}", e.getMessage());
            return "";
        }
    }

    // ─── Session validity check ───────────────────────────────────────────────

    private boolean isSessionValid() {
        return !cachedCookie.isEmpty()
                && !cachedCrumb.isEmpty()
                && (System.currentTimeMillis() - sessionFetchedAt) < SESSION_TTL_MS;
    }

    // ─── Browser-like headers (Yahoo blocks non-browser requests) ─────────────

    public HttpHeaders buildBrowserHeaders(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent",      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        headers.set("Accept",          "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        headers.set("Accept-Encoding", "identity");   // ← avoid gzip so RestTemplate can read it
        headers.set("Referer",         "https://finance.yahoo.com/");
        headers.set("Origin",          "https://finance.yahoo.com");
        headers.set("Connection",      "keep-alive");
        headers.set("Cache-Control",   "no-cache");
        headers.set("Pragma",          "no-cache");
        if (cookie != null && !cookie.isBlank()) {
            headers.set("Cookie", cookie);
        }
        return headers;
    }
}
