package ma.rh.ai.hr_workflow.integration.email.service;

public interface EmailService {
    String sendEmail(String configJson, String inputData, String workflowName, String workflowKey) throws Exception;
}
