package fr.master.reviewer.project;

/** Catégories de fichiers reconnues. Utilisées pour l'affichage et pour choisir quoi envoyer au LLM. */
public enum FileType {
    JAVA_SOURCE("Source Java"),
    TEST("Test"),
    BUILD("Build Maven/Gradle"),
    DOCKER("Docker"),
    CONFIG("Configuration"),
    DOCUMENTATION("Documentation"),
    SCRIPT("Script"),
    RESOURCE("Ressource"),
    OTHER("Autre");

    private final String label;

    FileType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
