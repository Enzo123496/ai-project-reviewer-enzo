package fr.master.reviewer.project;

/**
 * Charge un projet depuis une source (dossier, archive, dépôt Git...).
 * Ajouter une nouvelle source = écrire une nouvelle implémentation, sans toucher aux autres.
 */
public interface ProjectLoader {

    /** Indique si ce chargeur sait traiter la source donnée. */
    boolean supports(String source);

    Project load(String source) throws ProjectLoadException;
}
