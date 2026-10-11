package ma.rh.ai.hr_workflow.exceptions;

/** Thrown when creating/updating would violate a uniqueness rule (e.g. email already used). Mapped to HTTP 409. */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
