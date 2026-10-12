package ma.rh.ai.hr_workflow.exceptions.service;

public class EmailServiceException extends RuntimeException {
    public EmailServiceException(String message) {
        super(message);
    }

  public EmailServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
