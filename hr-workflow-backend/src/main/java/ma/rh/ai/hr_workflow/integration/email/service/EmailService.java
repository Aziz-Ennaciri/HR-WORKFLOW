package ma.rh.ai.hr_workflow.integration.email.service;

public interface EmailService {
    String sendEmail(String configJson, String inputData) throws Exception;
}
