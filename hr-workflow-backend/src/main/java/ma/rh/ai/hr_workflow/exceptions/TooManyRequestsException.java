package ma.rh.ai.hr_workflow.exceptions;

/** Mapped to HTTP 429. */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
