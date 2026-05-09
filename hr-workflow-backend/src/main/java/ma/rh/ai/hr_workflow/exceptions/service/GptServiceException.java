package ma.rh.ai.hr_workflow.exceptions.service;

public class GptServiceException extends RuntimeException {
    public GptServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
