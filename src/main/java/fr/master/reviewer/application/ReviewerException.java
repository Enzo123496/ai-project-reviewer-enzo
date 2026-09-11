package fr.master.reviewer.application;

/** Erreur "métier" présentable à l'utilisateur (message clair, sans détail technique inutile). */
public class ReviewerException extends Exception {
    public ReviewerException(String message) {
        super(message);
    }

    public ReviewerException(String message, Throwable cause) {
        super(message, cause);
    }
}
