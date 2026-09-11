package fr.master.reviewer.llm;

/** Réponse brute d'un modèle. Elle n'est PAS encore validée : c'est le rôle de LlmResultParser. */
public record LlmResponse(String content, String model, long promptTokens, long completionTokens, long durationMs) {
}
