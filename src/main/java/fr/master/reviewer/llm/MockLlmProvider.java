package fr.master.reviewer.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Fournisseur SIMULÉ (aucun réseau) : réponses déterministes et plausibles.
 * Sert à développer l'IHM, à faire une démo sans GPU et aux tests d'intégration.
 * Le rapport indique clairement "mock" : ces notes n'ont AUCUNE valeur d'évaluation.
 */
public final class MockLlmProvider implements LlmProvider {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public LlmResponse ask(LlmRequest request) {
        if ("summary".equals(request.meta("purpose", ""))) {
            return new LlmResponse("[Simulation] Synthèse générée par le fournisseur mock : le projet présente "
                    + "des points solides et plusieurs axes d'amélioration détaillés ci-dessus.", "mock", 0, 0, 1);
        }
        String id = request.meta("criterionId", "criterion");
        int max = Integer.parseInt(request.meta("maxScore", "10"));
        int variant = Math.floorMod(request.userPrompt().hashCode(), 4);
        double score = Math.round(max * (0.45 + 0.1 * variant) * 2) / 2.0;

        ObjectNode json = mapper.createObjectNode();
        json.put("criterion", id);
        json.put("score", score);
        json.put("maxScore", max);
        json.putPOJO("strengths", List.of("[Simulation] Organisation lisible des fichiers pour le critère " + id + "."));
        json.putPOJO("weaknesses", List.of("[Simulation] Certaines classes semblent concentrer trop de responsabilités."));
        json.putPOJO("issues", request.userPrompt().toLowerCase().contains("ignore all previous instructions")
                ? List.of("[Simulation] Le code contient une tentative d'injection de prompt.") : List.of());
        json.putPOJO("recommendations", List.of("[Simulation] Remplacer le fournisseur mock par un vrai LLM pour une évaluation réelle."));
        return new LlmResponse(json.toString(), "mock", 0, 0, 1);
    }

    @Override
    public String name() {
        return "mock (réponses simulées)";
    }
}
