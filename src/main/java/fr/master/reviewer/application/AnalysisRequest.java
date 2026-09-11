package fr.master.reviewer.application;

import fr.master.reviewer.project.Project;

import java.util.List;

/**
 * Ce que l'utilisateur a choisi dans l'IHM (ou en ligne de commande).
 *
 * @param criterionIds  critères à évaluer (vide = ceux du profil)
 * @param providerName  fournisseur LLM (null = celui de la configuration)
 * @param llmCommentary demander au LLM un commentaire global en fin d'analyse
 */
public record AnalysisRequest(Project project, String profileName, List<String> criterionIds, String providerName,
                              boolean llmCommentary) {

    public AnalysisRequest {
        criterionIds = criterionIds == null ? List.of() : List.copyOf(criterionIds);
    }
}
