package fr.master.reviewer.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.master.reviewer.config.AppConfig.ProviderSettings;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * PATTERN ADAPTER : traduit notre LlmRequest vers le format HTTP "chat/completions" popularisé par OpenAI,
 * et la réponse JSON du serveur vers notre LlmResponse.
 * Ce format est exposé par LM Studio, Ollama, llama.cpp, vLLM, l'API Mistral et l'API DeepSeek :
 * un seul adaptateur couvre donc tous ces fournisseurs (seuls baseUrl/model/clé changent).
 * Un fournisseur au format différent nécessiterait un NOUVEL adaptateur, sans toucher au reste.
 */
public final class OpenAiCompatibleProvider implements LlmProvider {

    private final ProviderSettings settings;
    private final HttpClient http;
    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiCompatibleProvider(ProviderSettings settings, HttpClient http, String apiKey) {
        this.settings = settings;
        this.http = http;
        this.apiKey = apiKey;
    }

    @Override
    public LlmResponse ask(LlmRequest request) throws LlmException {
        long start = System.currentTimeMillis();
        HttpRequest httpRequest = buildHttpRequest(request);
        HttpResponse<String> response;
        try {
            response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new LlmException(LlmException.Kind.TIMEOUT,
                    "pas de réponse en " + settings.timeoutSeconds() + " s", true, e);
        } catch (ConnectException e) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE,
                    "serveur injoignable (" + settings.baseUrl() + "). Le serveur LLM est-il démarré ?", true, e);
        } catch (IOException e) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE, "connexion interrompue : " + e.getMessage(), true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.Kind.INTERRUPTED, "appel interrompu", false, e);
        }
        return parse(response, System.currentTimeMillis() - start);
    }

    HttpRequest buildHttpRequest(LlmRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", settings.model());
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.systemPrompt());
        messages.addObject().put("role", "user").put("content", request.userPrompt());
        if ("json_object".equals(settings.responseFormat())) {
            body.putObject("response_format").put("type", "json_object");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(trimSlash(settings.baseUrl()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }

    private LlmResponse parse(HttpResponse<String> response, long durationMs) throws LlmException {
        int status = response.statusCode();
        if (status == 429 || status >= 500) {
            throw new LlmException(LlmException.Kind.HTTP_ERROR, "HTTP " + status + " " + excerpt(response.body()), true, null);
        }
        if (status == 401 || status == 403) {
            throw new LlmException(LlmException.Kind.CONFIGURATION, "HTTP " + status + " : clé d'API absente ou invalide");
        }
        if (status >= 400) {
            throw new LlmException(LlmException.Kind.HTTP_ERROR, "HTTP " + status + " " + excerpt(response.body()));
        }
        JsonNode root;
        try {
            root = mapper.readTree(response.body());
        } catch (IOException e) {
            throw new LlmException(LlmException.Kind.MALFORMED_RESPONSE, "le serveur n'a pas renvoyé de JSON", true, e);
        }
        JsonNode content = root == null ? null : root.path("choices").path(0).path("message").path("content");
        if (content == null || content.isMissingNode() || content.isNull()) {
            throw new LlmException(LlmException.Kind.MALFORMED_RESPONSE, "champ choices[0].message.content absent");
        }
        if (content.asText().isBlank()) {
            throw new LlmException(LlmException.Kind.EMPTY_RESPONSE, "réponse vide du modèle");
        }
        JsonNode usage = root.path("usage");
        String model = root.path("model").asText(settings.model());
        return new LlmResponse(content.asText(), model, usage.path("prompt_tokens").asLong(-1),
                usage.path("completion_tokens").asLong(-1), durationMs);
    }

    private static String excerpt(String body) {
        if (body == null) {
            return "";
        }
        String oneLine = body.replaceAll("\\s+", " ");
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "..." : oneLine;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public String name() {
        return settings.name() + " (" + settings.model() + ")";
    }
}
