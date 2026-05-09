package ma.rh.ai.hr_workflow.integration.gpt.service.Impl;

import java.io.IOException;
import java.time.LocalDateTime;

import ma.rh.ai.hr_workflow.exceptions.service.GptServiceException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.integration.gpt.dtos.GptConfigDTO;
import ma.rh.ai.hr_workflow.integration.gpt.dtos.GptRequestDTO;
import ma.rh.ai.hr_workflow.integration.gpt.dtos.GptResponseDTO;
import ma.rh.ai.hr_workflow.integration.gpt.service.GptService;

@Service
@RequiredArgsConstructor
public class GptServiceImpl implements GptService{
    private final ObjectMapper objectMapper;

    @Override
    public String analyze(String configJson, String inputData){
        try {
            GptConfigDTO config = objectMapper.readValue(configJson, GptConfigDTO.class);
            
            GptRequestDTO request = objectMapper.readValue(inputData, GptRequestDTO.class);

            Thread.sleep(500); 

            GptResponseDTO response = new GptResponseDTO();
            response.setAnalysis("This is a dummy GPT analysis result for: " + request.getText());
            response.setSentiment("positive");
            response.setConfidence(0.95);
            response.setModel(config.getModel() != null ? config.getModel() : "gpt-4");
            response.setTokensUsed(150);
            response.setAnalyzedAt(LocalDateTime.now());

            return objectMapper.writeValueAsString(response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GptServiceException("GPT request was interrupted",e);
        } catch (IOException e) {
            throw new GptServiceException("Error communicating with GPT service",e);
        } catch (Exception e) {
            throw new GptServiceException("Unexpected GPT service error",e);
        }
    }
    
}
