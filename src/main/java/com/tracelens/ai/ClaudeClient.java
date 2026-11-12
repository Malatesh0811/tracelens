package com.tracelens.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Asks Claude (Anthropic's Messages API) using Java's built-in HttpClient: no extra library.
 *
 * Request:  POST https://api.anthropic.com/v1/messages with a JSON body.
 * Response: JSON whose "content" is a list of blocks; the answer is in the "text" blocks.
 */
public class ClaudeClient implements LlmClient {

    static final String MODEL = "claude-opus-5-5";
    static final String API_VERSION = "2026-10-21";
    /** Lets the server retry on a fallback model if this model declines a request. */
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    static final int MAX_TOKENS = 16000;

    private static final URI API_URL = URI.create("https://api.anthropic.com/v1/messages");

    private final String apiKey;
    private final URI url;
    private final HttpClient http;

    public ClaudeClient(String apiKey) {
        this(apiKey, API_URL);
    }

    /** Tests pass the address of a small local server instead of the real one. */
    ClaudeClient(String apiKey, URI url) {
        this.apiKey = apiKey;
        this.url = url;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public String ask(String instructions, String question) throws LlmException {
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofMinutes(3))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("anthropic-beta", FALLBACK_BETA)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(instructions, question), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new LlmException("Could not reach the AI service (" + e.getClass().getSimpleName() + "). "
                    + "Check your internet connection.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("The AI request was interrupted.");
        }

        if (response.statusCode() != 200) {
            throw new LlmException(describeError(response.statusCode(), response.body()));
        }
        return answerFrom(response.body());
    }

    /** The JSON we send. Every text value goes through Json.quote, so quotes in a log cannot break it. */
    static String requestBody(String instructions, String question) {
        return "{"
                + "\"model\":" + Json.quote(MODEL) + ","
                + "\"max_tokens\":" + MAX_TOKENS + ","
                + "\"output_config\":{\"effort\":\"medium\"},"
                + "\"fallbacks\":\"default\","
                + "\"system\":" + Json.quote(instructions) + ","
                + "\"messages\":[{\"role\":\"user\",\"content\":" + Json.quote(question) + "}]"
                + "}";
    }

    /** Joins the "text" blocks of a successful response. Other blocks (like thinking) are skipped. */
    @SuppressWarnings("unchecked")
    static String answerFrom(String body) throws LlmException {
        Map<String, Object> message;
        try {
            message = (Map<String, Object>) Json.parse(body);
        } catch (IllegalArgumentException | ClassCastException e) {
            throw new LlmException("The AI service sent an answer TraceLens could not read.");
        }

        Object stopReason = message.get("stop_reason");
        if ("refusal".equals(stopReason)) {
            throw new LlmException("The AI declined to answer this request.");
        }

        StringBuilder answer = new StringBuilder();
        if (message.get("content") instanceof List<?> blocks) {
            for (Object block : blocks) {
                if (block instanceof Map<?, ?> map && "text".equals(map.get("type"))
                        && map.get("text") instanceof String text) {
                    answer.append(text);
                }
            }
        }
        if (answer.toString().isBlank()) {
            throw new LlmException("The AI returned no answer (stop reason: " + stopReason + ").");
        }
        return answer.toString().trim();
    }

    /** Turns an HTTP error into advice the user can act on. */
    static String describeError(int status, String body) {
        if (status == 401) {
            return "The API key was rejected. Check the ANTHROPIC_API_KEY environment variable.";
        }
        if (status == 403) {
            return "This API key is not allowed to use the AI model.";
        }
        if (status == 429) {
            return "Too many AI requests right now (rate limit). Wait a minute and try again.";
        }
        if (status >= 500) {
            return "The AI service is busy or having problems (HTTP " + status + "). Try again later.";
        }
        String detail = "";
        try {
            if (Json.parse(body) instanceof Map<?, ?> map && map.get("error") instanceof Map<?, ?> error
                    && error.get("message") instanceof String text) {
                detail = ": " + text;
            }
        } catch (IllegalArgumentException e) {
            // body was not JSON; show the status only
        }
        return "The AI request failed (HTTP " + status + ")" + detail;
    }
}
