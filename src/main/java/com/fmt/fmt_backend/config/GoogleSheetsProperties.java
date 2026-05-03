package com.fmt.fmt_backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "google.sheets")
@Data
public class GoogleSheetsProperties {
    private String sheetId;
    private String credentialsPath;
    private String tabName;
}
