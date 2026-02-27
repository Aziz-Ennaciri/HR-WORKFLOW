package ma.rh.ai.hr_workflow.integration.gpt.service;

public interface GptService {
    String analyze(String configJson, String inputData) throws Exception;
}
