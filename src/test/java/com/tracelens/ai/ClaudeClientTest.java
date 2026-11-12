package com.tracelens.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the real HTTP code against a tiny server running on this computer,
 * so no internet, no API key and no cost.
 */
class ClaudeClientTest {

    private static final String GOOD_ANSWER = """
            {"id": "msg_1", "type": "message", "role": "assistant", "stop_reason": "end_turn",
             "content": [{"type": "thinking", "thinking": ""},
                         {"type": "text", "text": "The database is not running."}]}""";

    private HttpServer server;
    private final Map<String, String> receivedHeaders = new HashMap<>();
    private String receivedBody;

    /** Starts a local server that answers every request with the given status and body. */
    private URI fakeApi(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); // port 0 = any free port
        server.createContext("/v1/messages", exchange -> {
            exchange.getRequestHeaders().forEach((name, values) -> receivedHeaders.put(name.toLowerCase(), values.get(0)));
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages");
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsTheRightRequestAndReadsTheTextAnswer() throws Exception {
        ClaudeClient client = new ClaudeClient("test-key", fakeApi(200, GOOD_ANSWER));

        String answer = client.ask("Be brief.", "Why did it crash?");

        assertEquals("The database is not running.", answer);
        assertEquals("test-key", receivedHeaders.get("x-api-key"));
        assertEquals(ClaudeClient.API_VERSION, receivedHeaders.get("anthropic-version"));
        assertEquals(ClaudeClient.FALLBACK_BETA, receivedHeaders.get("anthropic-beta"));

        Map<?, ?> body = (Map<?, ?>) Json.parse(receivedBody);
        assertEquals(ClaudeClient.MODEL, body.get("model"));
        assertEquals("Be brief.", body.get("system"));
        List<?> messages = (List<?>) body.get("messages");
        assertEquals("Why did it crash?", ((Map<?, ?>) messages.get(0)).get("content"));
    }

    @Test
    void requestBodyStaysValidJsonWithQuotesAndNewlinesInTheLog() {
        String question = "Root cause: \"user\" is null\n\tat C:\\app\\Main.java";

        Map<?, ?> body = (Map<?, ?>) Json.parse(ClaudeClient.requestBody("x", question));

        assertEquals(question, ((Map<?, ?>) ((List<?>) body.get("messages")).get(0)).get("content"));
    }

    @Test
    void wrongApiKeyGivesClearAdvice() throws IOException {
        ClaudeClient client = new ClaudeClient("bad-key", fakeApi(401,
                "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}"));

        LlmException error = assertThrows(LlmException.class, () -> client.ask("x", "y"));
        assertTrue(error.getMessage().contains("API key was rejected"));
    }

    @Test
    void busyServiceSaysTryLater() throws IOException {
        ClaudeClient client = new ClaudeClient("k", fakeApi(529, "{\"type\":\"error\"}"));

        LlmException error = assertThrows(LlmException.class, () -> client.ask("x", "y"));
        assertTrue(error.getMessage().contains("busy"));
    }

    @Test
    void otherErrorsShowTheServiceMessage() {
        String message = ClaudeClient.describeError(400,
                "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"max_tokens too large\"}}");

        assertEquals("The AI request failed (HTTP 400): max_tokens too large", message);
        assertEquals("The AI request failed (HTTP 400)", ClaudeClient.describeError(400, "<html>oops</html>"));
        assertTrue(ClaudeClient.describeError(429, "").contains("rate limit"));
    }

    @Test
    void refusalIsReportedNotShownAsAnAnswer() {
        LlmException error = assertThrows(LlmException.class, () -> ClaudeClient.answerFrom(
                "{\"stop_reason\":\"refusal\",\"content\":[{\"type\":\"text\",\"text\":\"partial\"}]}"));

        assertTrue(error.getMessage().contains("declined"));
    }

    @Test
    void emptyOrUnreadableAnswerIsAnError() {
        assertThrows(LlmException.class, () -> ClaudeClient.answerFrom("{\"stop_reason\":\"max_tokens\",\"content\":[]}"));
        assertThrows(LlmException.class, () -> ClaudeClient.answerFrom("not json"));
        assertThrows(LlmException.class, () -> ClaudeClient.answerFrom("[1, 2]"));
    }

    @Test
    void noServerGivesConnectionAdvice() {
        ClaudeClient client = new ClaudeClient("k", URI.create("http://127.0.0.1:1/v1/messages"));

        LlmException error = assertThrows(LlmException.class, () -> client.ask("x", "y"));
        assertTrue(error.getMessage().contains("Could not reach the AI service"));
    }
}
