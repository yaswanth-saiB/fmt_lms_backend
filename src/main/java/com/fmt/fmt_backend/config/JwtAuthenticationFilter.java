package com.fmt.fmt_backend.config;

import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.repository.UserRepository;
import com.fmt.fmt_backend.repository.UserSessionRepository;
import com.fmt.fmt_backend.service.CookieService;
import com.fmt.fmt_backend.service.CustomUserDetailsService;
import com.fmt.fmt_backend.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Intercepts every request and authenticates users via JWT.
 *
 * Token lookup order:
 *  1. access_token HttpOnly cookie  (used by browsers / React frontend)
 *  2. Authorization: Bearer <token> header (used by Swagger UI / API clients)
 *
 * This dual approach lets us test with Swagger while still using secure
 * cookie-based auth in production.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CookieService cookieService;
    private final CustomUserDetailsService userDetailsService;
    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        final String requestPath = request.getServletPath();
        log.debug("🔍 JWT Filter checking path: {}", requestPath);

        // 1. Try cookie first
        String jwt = null;
        Optional<String> cookieToken = cookieService.getAccessTokenFromCookies(request);
        if (cookieToken.isPresent()) {
            jwt = cookieToken.get();
            log.debug("🍪 JWT found in cookie for path: {}", requestPath);
        } else {
            // 2. Fallback to Authorization header (Swagger / API clients)
            String authHeader = request.getHeader("Authorization");
            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7);
                log.debug("📝 JWT found in Authorization header for path: {}", requestPath);
            }
        }

        if (jwt == null) {
            log.debug("⏩ No JWT found for path: {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String userEmail = jwtService.extractUsername(jwt);
            log.debug("📧 Extracted email from token: {}", userEmail);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                if (jwtService.isTokenValid(jwt, userDetails)) {

                    // 1. Check user account is still active (catches admin deactivation in real-time)
                    User user = userRepository.findByEmail(userEmail).orElse(null);
                    if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
                        log.warn("🚫 Rejected token for deactivated user: {}", userEmail);
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"success\":false,\"message\":\"Account is deactivated\"}");
                        return;
                    }

                    // 2. Validate session is still active in DB (catches device revocation in real-time)
                    UUID sessionId = jwtService.extractSessionId(jwt);
                    if (sessionId != null) {
                        boolean sessionActive = userSessionRepository
                                .findBySessionIdAndActiveTrue(sessionId)
                                .isPresent();
                        if (!sessionActive) {
                            log.warn("🚫 Rejected token — session revoked: {} user: {}", sessionId, userEmail);
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":false,\"message\":\"Session expired. Please log in again.\"}");
                            return;
                        }
                    }

                    log.debug("🔐 Token + session validated for user: {}", userEmail);

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.info("✅ Authenticated user: {} for path: {}", userEmail, requestPath);
                } else {
                    log.warn("❌ Token invalid for user: {}", userEmail);
                }
            }
        } catch (Exception e) {
            log.error("💥 JWT authentication failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getMethod().equals("OPTIONS");
    }
}
