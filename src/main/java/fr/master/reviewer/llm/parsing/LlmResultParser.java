package fr.master.reviewer.llm.parsing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.master.reviewer.analysis.Criterion;
import fr.master.reviewer.llm.LlmException;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforme le texte brut d'un LLM en LlmEvaluation VALIDÉE. Ne jamais faire confiance à la réponse :
 * - on tolère les écarts de forme fréquents (```json ... ```, texte autour, balises <think> des modèles
 *   de raisonnement) en extrayant le premier objet JSON équilibré ;
 * - on refuse ce qui est faux sur le fond (score absent, hors bornes, listes mal typées).
 */
public final class LlmResultParser {

    static final int MAX_ITEMS = 8;
    static final int MAX_ITEM_LENGTH = 400;

    private final ObjectMapper mapper = new ObjectMapper();

    public LlmEvaluation parse(String raw, Criterion criterion) throws LlmException {
        if (raw == null || raw.isBlank()) {
            throw new LlmException(LlmException.Kind.EMPTY_RESPONSE, "réponse vide");
        }
        String json = extractJsonObject(raw);
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw invalid("JSON mal formé : " + e.getOriginalMessage());
        }
        if (root == null || !root.isObject()) {
            throw invalid("la réponse n'est pas un objet JSON");
        }
        List<String> warnings = new ArrayList<>();

        JsonNode scoreNode = root.get("score");
        if (scoreNode == null || !scoreNode.isNumber()) {
            throw invalid("champ \"score\" absent ou non numérique");
        }
        double score = scoreNode.asDouble();
        JsonNode maxNode = root.get("maxScore");
        if (maxNode != null && maxNode.isNumber() && maxNode.asDouble() > 0
                && maxNode.asDouble() != criterion.maxScore()) {
            // Le modèle a utilisé une autre échelle (ex. /100) : on convertit et on le signale.
            score = score * criterion.maxScore() / maxNode.asDouble();
            warnings.add("Échelle renvoyée /" + maxNode.asInt() + " convertie en /" + criterion.maxScore());
        }
        if (Double.isNaN(score) || score < 0 || score > criterion.maxScore()) {
            throw invalid("score " + score + " hors de l'intervalle [0, " + criterion.maxScore() + "]");
        }

        JsonNode criterionNode = root.get("criterion");
        if (criterionNode != null && criterionNode.isTextual()
                && !criterionNode.asText().equalsIgnoreCase(criterion.id())
                && !criterionNode.asText().equalsIgnoreCase(criterion.name())) {
            warnings.add("Le modèle a nommé le critère \"" + criterionNode.asText() + "\" au lieu de \"" + criterion.id() + "\"");
        }

        List<String> strengths = stringList(root, "strengths", true);
        List<String> weaknesses = stringList(root, "weaknesses", true);
        List<String> issues = stringList(root, "issues", false);
        List<String> recommendations = stringList(root, "recommendations", true);

        // Contrôle de cohérence simple : note maximale mais beaucoup de faiblesses graves = suspect.
        if (score >= criterion.maxScore() * 0.9 && weaknesses.size() + issues.size() >= 4) {
            warnings.add("Réponse potentiellement incohérente : note très haute malgré de nombreux défauts listés");
        }
        return new LlmEvaluation(Math.round(score * 10) / 10.0, strengths, weaknesses, issues, recommendations, warnings);
    }

    /** Extrait le premier objet JSON {...} équilibré, en respectant les chaînes et échappements. */
    static String extractJsonObject(String raw) throws LlmException {
        String text = raw.replaceAll("(?s)<think>.*?</think>", "");
        int start = text.indexOf('{');
        if (start < 0) {
            throw invalid("aucun objet JSON trouvé dans la réponse");
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        throw invalid("objet JSON tronqué (accolade fermante manquante)");
    }

    private static List<String> stringList(JsonNode root, String field, boolean required) throws LlmException {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            if (required) {
                throw invalid("champ \"" + field + "\" absent");
            }
            return List.of();
        }
        if (!node.isArray()) {
            throw invalid("champ \"" + field + "\" doit être une liste");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw invalid("champ \"" + field + "\" doit contenir uniquement du texte");
            }
            String value = item.asText().strip();
            if (value.isEmpty() || values.size() >= MAX_ITEMS) {
                continue;
            }
            values.add(value.length() > MAX_ITEM_LENGTH ? value.substring(0, MAX_ITEM_LENGTH) + "..." : value);
        }
        return values;
    }

    private static LlmException invalid(String message) {
        return new LlmException(LlmException.Kind.INVALID_SCHEMA, message);
    }
}
