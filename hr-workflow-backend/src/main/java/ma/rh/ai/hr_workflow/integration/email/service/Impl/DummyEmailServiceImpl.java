package ma.rh.ai.hr_workflow.integration.email.service.Impl;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.integration.email.DTOs.EmailConfigDTO;
import ma.rh.ai.hr_workflow.integration.email.DTOs.EmailRequestDTO;
import ma.rh.ai.hr_workflow.integration.email.DTOs.EmailResponseDTO;
import ma.rh.ai.hr_workflow.integration.email.service.EmailService;

@Service
@RequiredArgsConstructor
public class DummyEmailServiceImpl implements EmailService{
    private final ObjectMapper objectMapper;

    @Override
    public String sendEmail(String configJson, String inputData, String workflowName, String workflowKey) throws Exception {
        try {
            EmailConfigDTO config = objectMapper.readValue(configJson, EmailConfigDTO.class);
            
            EmailRequestDTO request = objectMapper.readValue(inputData, EmailRequestDTO.class);

            Thread.sleep(100);

            EmailResponseDTO response = new EmailResponseDTO();
            response.setMessageId("msg_" + UUID.randomUUID().toString().substring(0, 8));
            response.setRecipient(request.getRecipient());
            response.setStatus("sent");
            response.setSentAt(LocalDateTime.now());

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            throw new RuntimeException();
        }
    }
}
