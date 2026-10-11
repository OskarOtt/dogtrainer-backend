package com.oskott.dogtrainerbackend.notification.service;

import com.oskott.dogtrainerbackend.notification.config.ExpoPushProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RestClientExpoPushClient implements ExpoPushClient {

    /** Expo accepts at most 100 messages per send request and 1000 ids per receipt request. */
    static final int MAX_MESSAGES_PER_REQUEST = 100;
    static final int MAX_RECEIPT_IDS_PER_REQUEST = 1000;

    private static final String ANDROID_CHANNEL_ID = "default";

    private final ExpoPushProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RestClientExpoPushClient(ExpoPushProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public List<Ticket> send(List<Message> messages) {
        List<Ticket> tickets = new ArrayList<>(messages.size());
        for (int from = 0; from < messages.size(); from += MAX_MESSAGES_PER_REQUEST) {
            List<Message> chunk = messages.subList(from, Math.min(from + MAX_MESSAGES_PER_REQUEST, messages.size()));
            JsonNode data = post(properties.pushUrl(), chunk.stream().map(this::toPayload).toList()).path("data");
            if (!data.isArray() || data.size() != chunk.size()) {
                throw new ExpoPushException("Unexpected Expo push response", null);
            }
            data.forEach(node -> tickets.add(toTicket(node)));
        }
        return tickets;
    }

    @Override
    public Map<String, Receipt> getReceipts(List<String> ticketIds) {
        Map<String, Receipt> receipts = new HashMap<>();
        for (int from = 0; from < ticketIds.size(); from += MAX_RECEIPT_IDS_PER_REQUEST) {
            List<String> chunk = ticketIds.subList(from, Math.min(from + MAX_RECEIPT_IDS_PER_REQUEST, ticketIds.size()));
            JsonNode data = post(properties.receiptsUrl(), Map.of("ids", chunk)).path("data");
            data.properties().forEach(entry -> {
                JsonNode node = entry.getValue();
                boolean ok = "ok".equals(node.path("status").asString(""));
                receipts.put(entry.getKey(), new Receipt(ok, ok ? null : errorOf(node)));
            });
        }
        return receipts;
    }

    private Map<String, Object> toPayload(Message message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("to", message.to());
        payload.put("title", message.title());
        payload.put("body", message.body());
        payload.put("sound", "default");
        payload.put("priority", "high");
        payload.put("channelId", ANDROID_CHANNEL_ID);
        if (message.data() != null) {
            payload.put("data", message.data());
        }
        if (message.badge() != null) {
            payload.put("badge", message.badge());
        }
        return payload;
    }

    private Ticket toTicket(JsonNode node) {
        if ("ok".equals(node.path("status").asString(""))) {
            return new Ticket(node.path("id").asString(null), null);
        }
        return new Ticket(null, errorOf(node));
    }

    private String errorOf(JsonNode node) {
        String detailsError = node.path("details").path("error").asString(null);
        return detailsError != null ? detailsError : node.path("message").asString("unknown error");
    }

    private JsonNode post(String url, Object body) {
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);
            if (properties.accessToken() != null && !properties.accessToken().isBlank()) {
                request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken());
            }
            String response = request.body(objectMapper.writeValueAsString(body)).retrieve().body(String.class);
            return objectMapper.readTree(response == null ? "{}" : response);
        } catch (RuntimeException e) {
            throw new ExpoPushException("Expo push request failed: " + e.getMessage(), e);
        }
    }
}