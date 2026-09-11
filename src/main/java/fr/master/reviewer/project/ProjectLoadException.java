package fr.master.reviewer.project;

public class ProjectLoadException extends Exception {
    public ProjectLoadException(String message) {
        super(message);
    }

    public ProjectLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
