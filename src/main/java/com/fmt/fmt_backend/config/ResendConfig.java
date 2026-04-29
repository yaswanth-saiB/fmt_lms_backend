package com.fmt.fmt_backend.config;

import com.resend.Resend;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ResendConfig {

    private final ResendProperties properties;

    @Bean
    public Resend resend() {
        return new Resend(properties.getApiKey());
    }
}
