package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.WhatsappTemplateDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WhatsAppApiService {

    private static final String GRAPH_API_BASE = "https://graph.facebook.com/v18.0";

    @Value("${whatsapp.access-token}")
    private String accessToken;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${whatsapp.waba-id:}")
    private String wabaId;

    private final RestTemplate restTemplate = new RestTemplate();

    public String sendTextMessage(String phone, String text) {
        return sendTextMessage(phone, text, null);
    }

    public String sendTextMessage(String phone, String text, String contextMessageId) {
        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", phone);
        body.put("type", "text");
        body.put("text", Map.of("body", text));
        if (contextMessageId != null && !contextMessageId.isBlank()) {
            body.put("context", Map.of("message_id", contextMessageId));
        }
        return send(body);
    }

    public String sendTemplateMessage(String phone, String templateName, List<String> params) {
        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", phone);
        body.put("type", "template");

        Map<String, Object> template = new HashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", "en"));

        if (params != null && !params.isEmpty()) {
            List<Map<String, String>> parameters = new ArrayList<>();
            for (String param : params) {
                parameters.add(Map.of("type", "text", "text", param));
            }
            template.put("components", List.of(Map.of("type", "body", "parameters", parameters)));
        }

        body.put("template", template);
        return send(body);
    }

    /**
     * Sends an interactive button message (max 3 buttons).
     * buttons: list of maps with keys "id" and "title"
     */
    public String sendInteractiveButtonMessage(String phone, String bodyText, List<Map<String, String>> buttons) {
        List<Map<String, Object>> buttonList = new ArrayList<>();
        for (Map<String, String> btn : buttons) {
            buttonList.add(Map.of(
                    "type", "reply",
                    "reply", Map.of("id", btn.get("id"), "title", btn.get("title"))
            ));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", phone);
        body.put("type", "interactive");
        body.put("interactive", Map.of(
                "type", "button",
                "body", Map.of("text", bodyText),
                "action", Map.of("buttons", buttonList)
        ));
        return send(body);
    }

    /**
     * Sends an interactive list message.
     * sections: list of maps with keys "title" and "rows" (each row has "id", "title", optional "description")
     */
    public String sendInteractiveListMessage(String phone, String bodyText, String buttonLabel,
                                              List<Map<String, Object>> sections) {
        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", phone);
        body.put("type", "interactive");
        body.put("interactive", Map.of(
                "type", "list",
                "body", Map.of("text", bodyText),
                "action", Map.of("button", buttonLabel, "sections", sections)
        ));
        return send(body);
    }

    @SuppressWarnings("unchecked")
    public List<WhatsappTemplateDto> getApprovedTemplates() {
        if (wabaId == null || wabaId.isBlank()) {
            log.warn("WHATSAPP_WABA_ID not configured — cannot fetch templates");
            return Collections.emptyList();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            String url = GRAPH_API_BASE + "/" + wabaId
                    + "/message_templates?fields=name,status,components,language,category&limit=200";
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getBody().get("data");
            if (data == null) return Collections.emptyList();
            return data.stream()
                    .filter(t -> "APPROVED".equals(t.get("status")))
                    .map(t -> {
                        String bodyText = extractBodyText(t);
                        return WhatsappTemplateDto.builder()
                                .name((String) t.get("name"))
                                .status((String) t.get("status"))
                                .category((String) t.get("category"))
                                .language((String) t.get("language"))
                                .bodyText(bodyText)
                                .paramCount(countParams(bodyText))
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch WhatsApp templates: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private String extractBodyText(Map<String, Object> template) {
        List<Map<String, Object>> components = (List<Map<String, Object>>) template.get("components");
        if (components == null) return "";
        return components.stream()
                .filter(c -> "BODY".equals(c.get("type")))
                .map(c -> (String) c.get("text"))
                .findFirst().orElse("");
    }

    private int countParams(String text) {
        if (text == null || text.isBlank()) return 0;
        Matcher m = Pattern.compile("\\{\\{\\d+}}").matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    public record MediaDownload(byte[] content, String mimeType) {}

    public MediaDownload downloadMedia(String mediaId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        // Step 1: get the CDN URL and mime_type
        ResponseEntity<Map> metaResp = restTemplate.exchange(
                GRAPH_API_BASE + "/" + mediaId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) metaResp.getBody();
        String url = meta != null ? (String) meta.get("url") : null;
        String mimeType = meta != null ? (String) meta.getOrDefault("mime_type", "application/octet-stream") : "application/octet-stream";

        if (url == null) throw new RuntimeException("No URL returned for media " + mediaId);

        // Step 2: download the actual binary
        ResponseEntity<byte[]> dataResp = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class);

        return new MediaDownload(dataResp.getBody(), mimeType);
    }

    private String send(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    GRAPH_API_BASE + "/" + phoneNumberId + "/messages",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    Map.class);

            if (response.getBody() != null) {
                // Extract the message ID from messages[0].id
                Object messages = response.getBody().get("messages");
                if (messages instanceof List<?> list && !list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof Map<?, ?> m) {
                        Object msgId = m.get("id");
                        if (msgId != null) {
                            return msgId.toString();
                        }
                    }
                }
            }
            log.warn("WhatsApp send: no message ID in response for payload to {}", payload.get("to"));
            return null;
        } catch (Exception e) {
            log.error("WhatsApp API send failed: {}", e.getMessage());
            throw new RuntimeException("WhatsApp send failed: " + e.getMessage(), e);
        }
    }
}
