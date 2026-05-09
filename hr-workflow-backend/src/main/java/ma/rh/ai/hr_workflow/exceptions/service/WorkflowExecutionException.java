package ma.rh.ai.hr_workflow.exceptions.service;

public class WorkflowExecutionException extends RuntimeException {
    public WorkflowExecutionException(String message) {
        super(message);
    }

    public WorkflowExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
