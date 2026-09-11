package fr.master.reviewer.llm.parsing;

import java.util.List;

/** Réponse d'un LLM APRÈS validation : on peut lui faire confiance structurellement. */
public record LlmEvaluation(double score, List<String> strengths, List<String> weaknesses, List<String> issues,
                            List<String> recommendations, List<String> warnings) {

    public LlmEvaluation {
        strengths = List.copyOf(strengths);
        weaknesses = List.copyOf(weaknesses);
        issues = List.copyOf(issues);
        recommendations = List.copyOf(recommendations);
        warnings = List.copyOf(warnings);
    }
}
