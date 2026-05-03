package com.fmt.fmt_backend.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.util.Collections;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class GoogleSheetsConfig {

    private final GoogleSheetsProperties properties;

    @Bean
    @ConditionalOnProperty(name = "google.sheets.credentials-path")
    public Sheets sheetsClient() throws Exception {
        log.info("🔧 Initialising Google Sheets client — credentials: {}", properties.getCredentialsPath());

        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new FileInputStream(properties.getCredentialsPath()))
                .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY));

        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("FMT Backend")
                .build();
    }
}
