package com.fmt.fmt_backend.config;

import com.fmt.fmt_backend.service.CustomUserDetailsService;
import com.fmt.fmt_backend.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        final String requestPath = request.getServletPath();
        log.debug("🔍 JWT Filter checking path: {}", requestPath);

        // Get JWT token from request
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // Check if Authorization header exists and starts with "Bearer "
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            log.debug("⏩ No Bearer token found for path: {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        // Extract JWT token (remove "Bearer " prefix)
        jwt = authHeader.substring(7);
        log.debug("📝 JWT token extracted (length: {})", jwt.length());

        try {
            // Extract username from JWT
            userEmail = jwtService.extractUsername(jwt);
            log.debug("📧 Extracted email from token: {}", userEmail);

            // IMPORTANT: Always check if userEmail is not null
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Load user details from database
                log.debug("👤 Loading user details for: {}", userEmail);
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
                log.debug("✅ User details loaded successfully");

                // Validate token
                if (jwtService.isTokenValid(jwt, userDetails)) {
                    log.debug("🔐 Token validated successfully");

                    // Create authentication token
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null, // credentials are null because we use JWT
                                    userDetails.getAuthorities()
                            );

                    // Add request details
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    // Set authentication in context
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.info("✅ Authenticated user: {} for path: {}", userEmail, requestPath);
                } else {
                    log.warn("❌ Token invalid for user: {}", userEmail);
                }
            } else {
                log.debug("ℹ️ User email is null or authentication already exists in context");
            }
        } catch (Exception e) {
            log.error("💥 JWT authentication failed: {}", e.getMessage());
            // Don't throw - let the request continue
        }

        filterChain.doFilter(request, response);
    }
}