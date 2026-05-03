package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.config.GoogleSheetsProperties;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleSheetsService {

    // Optional — bean only exists when GOOGLE_SHEETS_CREDENTIALS_PATH is configured
    @Setter
    @Autowired(required = false)
    private Sheets sheetsClient;

    private final GoogleSheetsProperties properties;

    public List<List<Object>> readAllRows() throws IOException {
        if (sheetsClient == null) {
            throw new IllegalStateException(
                    "Google Sheets is not configured. Set GOOGLE_SHEETS_CREDENTIALS_PATH in your environment.");
        }
        String range = properties.getTabName() + "!A:Z";
        log.info("📄 Reading sheet: {} tab: {}", properties.getSheetId(), properties.getTabName());

        ValueRange response = sheetsClient.spreadsheets().values()
                .get(properties.getSheetId(), range)
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            log.info("📄 Sheet returned no rows");
            return Collections.emptyList();
        }

        log.info("📄 Read {} rows from Google Sheet", values.size());
        return values;
    }
}
