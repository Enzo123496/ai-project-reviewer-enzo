package fr.master.reviewer.analysis;

import fr.master.reviewer.llm.LlmProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * FACTORY + registre : associe une clé ("llm-code", "test-presence"...) à une fonction de création
 * d'analyseur. Ajouter une nouvelle méthode d'analyse = une classe + UNE ligne d'enregistrement
 * (dans AppFactory). Ajouter un critère utilisant une méthode existante = zéro ligne de Java.
 */
public final class AnalyzerRegistry {

    @FunctionalInterface
    public interface AnalyzerCreator {
        Analyzer create(Criterion criterion, LlmProvider llm);
    }

    private final Map<String, AnalyzerCreator> creators = new LinkedHashMap<>();

    public AnalyzerRegistry register(String key, AnalyzerCreator creator) {
        if (creators.putIfAbsent(key, creator) != null) {
            throw new IllegalStateException("Analyseur déjà enregistré : " + key);
        }
        return this;
    }

    public Analyzer create(Criterion criterion, LlmProvider llm) {
        AnalyzerCreator creator = creators.get(criterion.analyzer());
        if (creator == null) {
            throw new IllegalArgumentException("Aucun analyseur '" + criterion.analyzer()
                    + "' pour le critère " + criterion.id() + ". Disponibles : " + creators.keySet());
        }
        return creator.create(criterion, llm);
    }

    /** Critères dont l'analyseur n'est pas enregistré : à vérifier AU DÉMARRAGE plutôt qu'en pleine analyse. */
    public java.util.List<String> unknownAnalyzers(java.util.List<Criterion> criteria) {
        return criteria.stream().filter(c -> !creators.containsKey(c.analyzer()))
                .map(c -> "critère '" + c.id() + "' -> analyseur '" + c.analyzer() + "'").toList();
    }

    public Set<String> keys() {
        return creators.keySet();
    }
}
