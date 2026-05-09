package ma.rh.ai.hr_workflow.exceptions.service;

public class DriveServiceException extends RuntimeException {
    public DriveServiceException(String message) {
        super(message);
    }

    public DriveServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
