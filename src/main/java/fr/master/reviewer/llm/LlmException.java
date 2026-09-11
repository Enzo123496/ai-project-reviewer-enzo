package fr.master.reviewer.llm;

/**
 * Toutes les pannes possibles d'un LLM, classées. Le champ "retryable" permet au décorateur de retry
 * de savoir s'il est utile de réessayer (un timeout : oui ; une clé d'API invalide : non).
 */
public class LlmException extends Exception {

    public enum Kind {
        TIMEOUT(true),
        UNAVAILABLE(true),
        HTTP_ERROR(false),
        EMPTY_RESPONSE(true),
        MALFORMED_RESPONSE(true),
        INVALID_SCHEMA(false),
        CONFIGURATION(false),
        INTERRUPTED(false);

        private final boolean retryableByDefault;

        Kind(boolean retryableByDefault) {
            this.retryableByDefault = retryableByDefault;
        }
    }

    private final Kind kind;
    private final boolean retryable;

    public LlmException(Kind kind, String message) {
        this(kind, message, kind.retryableByDefault, null);
    }

    public LlmException(Kind kind, String message, boolean retryable, Throwable cause) {
        super(kind + " : " + message, cause);
        this.kind = kind;
        this.retryable = retryable;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
