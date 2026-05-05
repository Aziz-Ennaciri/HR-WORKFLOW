package ma.rh.ai.hr_workflow.integration.drive.service.Impl;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.boot.beanvalidation.IntegrationException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.integration.drive.DTOs.DriveConfigDTO;
import ma.rh.ai.hr_workflow.integration.drive.DTOs.DriveRequestDTO;
import ma.rh.ai.hr_workflow.integration.drive.DTOs.DriveResponseDTO;
import ma.rh.ai.hr_workflow.integration.drive.service.DriveService;

@Service
@RequiredArgsConstructor
public class DummyDriveServiceImpl implements DriveService{
    private final ObjectMapper objectMapper;

    @Override
    public String saveFile(String configJson, String inputData) throws Exception {
        try {
            DriveConfigDTO config = objectMapper.readValue(configJson, DriveConfigDTO.class);
            
            DriveRequestDTO request = objectMapper.readValue(inputData, DriveRequestDTO.class);

            Thread.sleep(200);

            String fileId = UUID.randomUUID().toString().substring(0, 20);
            DriveResponseDTO response = new DriveResponseDTO();
            response.setFileId(fileId);
            response.setFileName(request.getFileName());
            response.setFileUrl("https://drive.google.com/file/d/" + fileId + "/view");
            response.setWebViewLink("https://drive.google.com/file/d/" + fileId);
            response.setSize(1024L);
            response.setCreatedAt(LocalDateTime.now());

            return objectMapper.writeValueAsString(response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IntegrationException("DriveService");
        } catch (Exception e) {
            throw new IntegrationException("DriveService");
        }
    }
}
