package com.fmt.fmt_backend.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;

/**
 * Manages HttpOnly cookies for access and refresh tokens.
 *
 * Cookie strategy:
 *  - access_token  : path=/, expires with access token (6 hrs default)
 *  - refresh_token : path=/api/auth/token, expires with refresh token (14 days default)
 *    The restricted path means the browser only sends the refresh cookie to the refresh endpoint,
 *    limiting its exposure.
 *
 * Both cookies are HttpOnly (JS cannot read them) and SameSite=Strict (no CSRF risk).
 * In production set COOKIE_SECURE=true so they are only sent over HTTPS.
 */
@Service
@Slf4j
public class CookieService {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    @Value("${app.cookie.secure:false}")
    private boolean secure;

    @Value("${app.cookie.domain:}")
    private String domain;

    @Value("${jwt.access-token-expiration:21600000}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration:1209600000}")
    private long refreshTokenExpirationMs;

    // ========== BUILD HEADERS (attach to ResponseEntity) ==========

    public HttpHeaders buildAuthCookieHeaders(String accessToken, String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE,
                buildCookieString(ACCESS_TOKEN_COOKIE, accessToken, "/", accessTokenExpirationMs / 1000));
        headers.add(HttpHeaders.SET_COOKIE,
                buildCookieString(REFRESH_TOKEN_COOKIE, refreshToken, "/api/auth/token", refreshTokenExpirationMs / 1000));
        log.debug("Auth cookie headers built (secure={})", secure);
        return headers;
    }

    public HttpHeaders buildClearCookieHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE,
                buildCookieString(ACCESS_TOKEN_COOKIE, "", "/", 0));
        headers.add(HttpHeaders.SET_COOKIE,
                buildCookieString(REFRESH_TOKEN_COOKIE, "", "/api/auth/token", 0));
        log.debug("Clear cookie headers built");
        return headers;
    }

    // ========== GET ==========

    public Optional<String> getAccessTokenFromCookies(HttpServletRequest request) {
        return getCookieValue(request, ACCESS_TOKEN_COOKIE);
    }

    public Optional<String> getRefreshTokenFromCookies(HttpServletRequest request) {
        return getCookieValue(request, REFRESH_TOKEN_COOKIE);
    }

    // ========== PRIVATE ==========

    private String buildCookieString(String name, String value, String path, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .maxAge(maxAgeSeconds)
                .sameSite("Strict");

        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }

        return builder.build().toString();
    }

    private Optional<String> getCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
