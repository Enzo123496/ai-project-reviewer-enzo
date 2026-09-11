package fr.master.reviewer.analysis;

import fr.master.reviewer.project.FileType;

import java.util.Set;

/**
 * Un critère d'évaluation, décrit dans config/reviewer.json (donc ajoutable SANS recompiler).
 *
 * @param analyzer clé de l'analyseur à utiliser dans l'AnalyzerRegistry ("llm-code", "llm-structure",
 *                 "test-presence", ...). C'est ce qui relie la configuration au code.
 * @param fileTypes types de fichiers pertinents pour ce critère (vide = tous)
 * @param weight    poids dans la note globale
 */
public record Criterion(String id, String name, String description, int maxScore, String analyzer,
                        Set<FileType> fileTypes, double weight) {

    public Criterion {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Un critère doit avoir un id");
        }
        name = name == null ? id : name;
        description = description == null ? "" : description;
        maxScore = maxScore <= 0 ? 10 : maxScore;
        analyzer = analyzer == null ? "llm-code" : analyzer;
        fileTypes = fileTypes == null ? Set.of() : Set.copyOf(fileTypes);
        weight = weight <= 0 ? 1.0 : weight;
    }
}
