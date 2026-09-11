package fr.master.reviewer.analysis;

/**
 * PATTERN STRATEGY : chaque façon d'évaluer un critère (statique, LLM sur le code, LLM sur la structure,
 * exécution dans Docker...) est une stratégie interchangeable derrière cette interface.
 * Le moteur ne connaît que cette interface.
 */
public interface Analyzer {

    CriterionResult analyze(AnalysisContext context);
}
