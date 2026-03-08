package com.fmt.fmt_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * Configure RestTemplate with appropriate timeouts
     * Note: Using Duration-based methods to avoid deprecation warnings
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate(clientHttpRequestFactory());
    }

    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // Set connect timeout (time to establish connection)
        // Using the Duration-based setter to avoid deprecation
        factory.setConnectTimeout(Duration.ofSeconds(10));

        // Set read timeout (time to wait for response)
        factory.setReadTimeout(Duration.ofSeconds(30));

        return factory;
    }
}