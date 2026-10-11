package com.oskott.dogtrainerbackend.notification;

import com.oskott.dogtrainerbackend.notification.config.ExpoPushProperties;
import com.oskott.dogtrainerbackend.notification.service.ExpoPushClient;
import com.oskott.dogtrainerbackend.notification.service.RestClientExpoPushClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientExpoPushClientTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final List<String> sendBodies = new CopyOnWriteArrayList<>();
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private volatile String sendResponse = "{}";
    private volatile String receiptsResponse = "{}";
    private volatile int sendStatus = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/send", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            sendBodies.add(body);
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth != null) {
                authHeaders.add(auth);
            }
            respond(exchange, sendStatus, sendResponse != null ? sendResponse : okTickets(objectMapper.readTree(body).size()));
        });
        server.createContext("/receipts", exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, receiptsResponse);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendMapsTicketsInOrderAndSendsTheExpectedPayload() throws Exception {
        sendResponse = """
                {"data":[{"status":"ok","id":"ticket-1"},
                         {"status":"error","message":"gone","details":{"error":"DeviceNotRegistered"}}]}
                """;

        List<ExpoPushClient.Ticket> tickets = client("secret-token").send(List.of(
                new ExpoPushClient.Message("ExponentPushToken[a]", "Title", "Body", Map.of("type", "COMMENT"), 3),
                new ExpoPushClient.Message("ExponentPushToken[b]", "Title", "Body", null, null)
        ));

        assertThat(tickets).hasSize(2);
        assertThat(tickets.get(0).accepted()).isTrue();
        assertThat(tickets.get(0).id()).isEqualTo("ticket-1");
        assertThat(tickets.get(1).accepted()).isFalse();
        assertThat(tickets.get(1).error()).isEqualTo("DeviceNotRegistered");

        JsonNode sent = objectMapper.readTree(sendBodies.get(0));
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).get("to").asString()).isEqualTo("ExponentPushToken[a]");
        assertThat(sent.get(0).get("data").get("type").asString()).isEqualTo("COMMENT");
        assertThat(sent.get(0).get("badge").asInt()).isEqualTo(3);
        assertThat(sent.get(1).has("badge")).isFalse();
        assertThat(authHeaders).containsExactly("Bearer secret-token");
    }

    @Test
    void sendBatchesMoreThanOneHundredMessages() {
        sendResponse = null;
        List<ExpoPushClient.Message> messages = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            messages.add(new ExpoPushClient.Message("ExponentPushToken[" + i + "]", "t", "b", null, null));
        }

        List<ExpoPushClient.Ticket> tickets = client(null).send(messages);

        assertThat(tickets).hasSize(150);
        assertThat(sendBodies).hasSize(2);
        assertThat(authHeaders).isEmpty();
    }

    @Test
    void getReceiptsParsesOkAndErrorReceipts() {
        receiptsResponse = """
                {"data":{"a":{"status":"ok"},
                         "b":{"status":"error","message":"nope","details":{"error":"DeviceNotRegistered"}}}}
                """;

        Map<String, ExpoPushClient.Receipt> receipts = client(null).getReceipts(List.of("a", "b", "c"));

        assertThat(receipts).containsOnlyKeys("a", "b");
        assertThat(receipts.get("a").ok()).isTrue();
        assertThat(receipts.get("b").ok()).isFalse();
        assertThat(receipts.get("b").error()).isEqualTo("DeviceNotRegistered");
    }

    @Test
    void httpErrorsAreSurfacedAsExpoPushException() {
        sendStatus = 500;
        sendResponse = "{\"errors\":[{\"code\":\"INTERNAL\"}]}";

        assertThatThrownBy(() -> client(null).send(List.of(
                new ExpoPushClient.Message("ExponentPushToken[a]", "t", "b", null, null))))
                .isInstanceOf(ExpoPushClient.ExpoPushException.class);
    }

    private RestClientExpoPushClient client(String accessToken) {
        String base = "http://localhost:" + server.getAddress().getPort();
        return new RestClientExpoPushClient(
                new ExpoPushProperties(base + "/send", base + "/receipts", accessToken, Duration.ZERO, Duration.ofMinutes(5)),
                objectMapper);
    }

    private static String okTickets(int count) {
        StringBuilder data = new StringBuilder("{\"data\":[");
        for (int i = 0; i < count; i++) {
            data.append(i > 0 ? "," : "").append("{\"status\":\"ok\",\"id\":\"t-").append(i).append("\"}");
        }
        return data.append("]}").toString();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
