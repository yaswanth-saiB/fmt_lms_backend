package com.fmt.fmt_backend.config;

import com.fmt.fmt_backend.service.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF (we use JWT tokens, not sessions)
                .csrf(csrf -> csrf.disable())

                // Configure CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Exception handling - remove custom entry point for now
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            String uri = request.getRequestURI();
                            // Only log real API auth failures — suppress bot scanner noise
                            // (bots probe for .env, secrets.yml, actuator/env, etc.)
                            if (uri.startsWith("/api/")) {
                                log.warn("🔐 Authentication failed: {} {}", request.getMethod(), uri);
                            } else {
                                log.debug("🔐 Authentication failed (bot scan?): {} {}", request.getMethod(), uri);
                            }
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\": false, \"message\": \"Authentication required\"}");
                        })
                )

                // Authorize requests
                .authorizeHttpRequests(auth -> auth
                        // Public — no token required
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Auth — public signup & login
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/login/verify-otp").permitAll()
                        .requestMatchers("/api/auth/signup/**").permitAll()
                        // Token endpoints — refresh/rotate must be public because the
                        // access token may be expired when the browser calls them
                        .requestMatchers("/api/auth/token/refresh").permitAll()
                        .requestMatchers("/api/auth/token/rotate").permitAll()
                        // Other public auth
                        .requestMatchers("/api/auth/forgot-password").permitAll()
                        .requestMatchers("/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/enquiry/submit").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews").permitAll()
                        // Docs & infra
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Testing
                        .requestMatchers("/api/test/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        // Webhooks — called by Zoom, Bunny, and Meta (WhatsApp/Lead Gen)
                        .requestMatchers("/api/webhook/zoom").permitAll()
                        .requestMatchers("/api/webhook/bunny").permitAll()
                        .requestMatchers("/api/webhook/whatsapp").permitAll()

                        // Protected — require valid access token
                        .requestMatchers("/api/auth/logout").authenticated()
                        .requestMatchers("/api/meetings/**").authenticated()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/auth/change-password").authenticated()
                        .requestMatchers("/api/auth/token/**").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/mentor/**").hasRole("MENTOR")
                        .requestMatchers("/api/student/**").hasRole("STUDENT")
                        .requestMatchers("/api/recordings/**").hasRole("STUDENT")
                        .requestMatchers("/api/user/**").authenticated()
                        .requestMatchers("/api/devices/**").authenticated()
                        // Lead Management CRM — ADMIN or SALES
                        // assign endpoint is ADMIN-only, enforced inside LeadService
                        .requestMatchers("/api/sales/**").hasAnyRole("ADMIN", "SALES")
                        // WhatsApp Inbox — ADMIN or SALES
                        .requestMatchers("/api/inbox/**").hasAnyRole("ADMIN", "SALES")
                        // Chatbot admin — ADMIN only
                        .requestMatchers("/api/admin/chatbot/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )

                // Session management (stateless - we use JWT)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Authentication provider
                .authenticationProvider(authenticationProvider())

                // Add JWT filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        log.info("🚀 CORS Config loaded - new version");
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",                    // React dev (CRA)
                "http://localhost:5173",                    // React dev (Vite)
                "https://firstmilliontrade.com",           // Home page
                "https://www.firstmilliontrade.com",       // Home page (www)
                "https://app.firstmilliontrade.com",       // Login/signup app
                "https://www.app.firstmilliontrade.com",   // Login/signup app (www)
                "https://api.firstmilliontrade.com",       // Backend self-reference (prod)
                "https://dev.firstmilliontrade.com",       // Dev frontend
                "https://www.dev.firstmilliontrade.com",   // Dev frontend (www)
                "https://dev-api.firstmilliontrade.com"    // Backend self-reference (dev)
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Requested-With",
                "Accept", "Origin", "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Set-Cookie"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with strength 12 (production recommended)
        return new BCryptPasswordEncoder(12);
    }
}
