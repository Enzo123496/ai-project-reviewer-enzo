package fr.master.reviewer.llm;

/**
 * Point d'accès UNIQUE aux modèles de langage. Aucune autre classe de l'application ne fait d'appel HTTP
 * vers un LLM. Remplacer Mistral par Llama ou DeepSeek = changer l'implémentation (via la configuration).
 */
public interface LlmProvider {

    LlmResponse ask(LlmRequest request) throws LlmException;

    /** Nom lisible, affiché dans le rapport (ex. "lmstudio (mistral-7b-instruct)"). */
    String name();
}
