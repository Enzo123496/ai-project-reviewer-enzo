package fr.master.reviewer.analysis;

import fr.master.reviewer.llm.parsing.LlmResultParser;
import fr.master.reviewer.llm.prompt.UntrustedContentGuard;
import fr.master.reviewer.security.SecretRedactor;

/** Objet-paramètre regroupant les collaborateurs et réglages communs à tous les analyseurs LLM. */
public record LlmToolkit(LlmResultParser parser, UntrustedContentGuard guard, SecretRedactor redactor,
                         ResultAggregator aggregator, String outputLanguage, double temperature, int maxTokens,
                         int maxCharsPerChunk, int maxChunks) {
}
