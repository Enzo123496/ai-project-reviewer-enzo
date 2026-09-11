package fr.master.reviewer.llm;

import com.sun.net.httpserver.HttpServer;
import fr.master.reviewer.config.AppConfig.ProviderSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Teste l'adaptateur HTTP contre un faux serveur local (JDK HttpServer) : aucun vrai LLM nécessaire. */
class OpenAiCompatibleProviderTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final LlmRequest request = new LlmRequest("system", "user", 0, 100, Map.of());

    private OpenAiCompatibleProvider serverReplying(int status, String body, long delayMs, int timeoutSeconds) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        return new OpenAiCompatibleProvider(new ProviderSettings("test", "openai-compatible", url, "modele-test", null, "none", timeoutSeconds),
                HttpClient.newHttpClient(), "cle-secrete-123");
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsStructuredRequestAndReadsContent() throws Exception {
        LlmResponse r = serverReplying(200, "{\"model\":\"m\",\"choices\":[{\"message\":{\"content\":\"bonjour\"}}],"
                + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":3}}", 0, 5).ask(request);

        assertEquals("bonjour", r.content());
        assertEquals(12, r.promptTokens());
        assertTrue(lastBody.get().contains("\"role\":\"system\""));
        assertTrue(lastBody.get().contains("\"model\":\"modele-test\""));
        assertEquals("Bearer cle-secrete-123", lastAuth.get());
    }

    @Test
    void serverErrorIsRetryable() throws Exception {
        LlmException e = assertThrows(LlmException.class, () -> serverReplying(503, "busy", 0, 5).ask(request));
        assertEquals(LlmException.Kind.HTTP_ERROR, e.kind());
        assertTrue(e.isRetryable());
    }

    @Test
    void unauthorizedIsAConfigurationError() throws Exception {
        LlmException e = assertThrows(LlmException.class, () -> serverReplying(401, "no", 0, 5).ask(request));
        assertEquals(LlmException.Kind.CONFIGURATION, e.kind());
    }

    @Test
    void malformedAndEmptyBodies() throws Exception {
        assertEquals(LlmException.Kind.MALFORMED_RESPONSE,
                assertThrows(LlmException.class, () -> serverReplying(200, "<html>", 0, 5).ask(request)).kind());
        stop();
        assertEquals(LlmException.Kind.EMPTY_RESPONSE, assertThrows(LlmException.class,
                () -> serverReplying(200, "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}", 0, 5).ask(request)).kind());
    }

    @Test
    void timeoutIsDetected() throws Exception {
        LlmException e = assertThrows(LlmException.class, () -> serverReplying(200, "{}", 2500, 1).ask(request));
        assertEquals(LlmException.Kind.TIMEOUT, e.kind());
    }

    @Test
    void stoppedServerIsUnavailable() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(new ProviderSettings("down", "openai-compatible",
                "http://127.0.0.1:" + port + "/v1", "m", null, "none", 2), HttpClient.newHttpClient(), null);
        assertEquals(LlmException.Kind.UNAVAILABLE, assertThrows(LlmException.class, () -> provider.ask(request)).kind());
    }
}
