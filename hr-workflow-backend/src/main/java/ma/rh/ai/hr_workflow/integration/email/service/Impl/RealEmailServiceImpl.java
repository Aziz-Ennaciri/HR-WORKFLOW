package ma.rh.ai.hr_workflow.integration.email.service.Impl;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.integration.email.DTOs.EmailConfigDTO;
import ma.rh.ai.hr_workflow.integration.email.DTOs.EmailRequestDTO;
import ma.rh.ai.hr_workflow.integration.email.DTOs.EmailResponseDTO;
import ma.rh.ai.hr_workflow.integration.email.service.EmailService;

@Slf4j
@Service
@Primary  // ✅ This makes it the default implementation
@RequiredArgsConstructor
public class RealEmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;

    @Override
    public String sendEmail(String configJson, String inputData, String workflowName, String workflowKey) throws Exception {
        try {
            log.info("Sending real email...");

            // Parse configuration
            EmailConfigDTO config = objectMapper.readValue(configJson, EmailConfigDTO.class);
            EmailRequestDTO request = objectMapper.readValue(inputData, EmailRequestDTO.class);

            // Get email details from config
            String to = config.getTo();
            String subject = config.getSubject() != null ? config.getSubject() : "Workflow Notification";
            String body = config.getBody() != null ? config.getBody() : "";

            // If recipient not in config, use from request
            if (to == null || to.isEmpty()) {
                to = request.getRecipient();
            }

            if (to == null || to.isEmpty()) {
                throw new RuntimeException("Email recipient is required");
            }

            // Check if this is a CV/recruitment workflow
            boolean isCvWorkflow = isCvRecruitmentWorkflow(workflowName, workflowKey);

            if (isCvWorkflow) {
                // Customize email for CV recruitment workflow
                subject = customizeSubjectForCvWorkflow(subject, workflowName);
                body = customizeBodyForCvWorkflow(body, inputData, workflowName);
                log.info("📧 Sending CV recruitment notification email");
            }

            // Create and send email
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);

            log.info("✅ Email sent successfully to: {}", to);

            // Create response
            EmailResponseDTO response = new EmailResponseDTO();
            response.setMessageId("msg_" + UUID.randomUUID().toString().substring(0, 8));
            response.setRecipient(to);
            response.setStatus("sent");
            response.setSentAt(LocalDateTime.now());

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("❌ Failed to send email", e);
            throw new RuntimeException("Email sending failed: " + e.getMessage());
        }
    }

    /**
     * Check if this is a CV/recruitment workflow
     */
    private boolean isCvRecruitmentWorkflow(String workflowName, String workflowKey) {
        if (workflowName == null || workflowKey == null) return false;

        String name = workflowName.toLowerCase();
        String key = workflowKey.toLowerCase();

        // Check for CV/recruitment keywords
        return name.contains("cv") || name.contains("recruitment") || name.contains("candidate") ||
               name.contains("hiring") || name.contains("job") || name.contains("rectrutemnt") ||
               key.contains("cv") || key.contains("recruitment") || key.contains("candidate") ||
               key.contains("hiring") || key.contains("job");
    }

    /**
     * Customize subject for CV workflow
     */
    private String customizeSubjectForCvWorkflow(String originalSubject, String workflowName) {
        if (originalSubject != null && !originalSubject.isEmpty()) {
            return originalSubject;
        }
        return "CV Analysis Complete - " + workflowName;
    }

    /**
     * Customize body for CV workflow with Excel file information
     */
    private String customizeBodyForCvWorkflow(String originalBody, String inputData, String workflowName) {
        StringBuilder body = new StringBuilder();

        // Add original body if present
        if (originalBody != null && !originalBody.isEmpty()) {
            body.append(originalBody).append("\n\n");
        }

        body.append("🎯 CV Recruitment Analysis Complete\n\n");
        body.append("Your ").append(workflowName).append(" workflow has successfully processed candidate CVs.\n\n");

        // Try to extract Excel file information from inputData
        try {
            // inputData should contain the Excel response from the previous node
            var excelData = objectMapper.readTree(inputData);

            if (excelData.has("rowsProcessed")) {
                int rows = excelData.get("rowsProcessed").asInt();
                int columns = excelData.get("columnsProcessed").asInt();
                String sheetName = excelData.get("sheetName").asText("Data");

                body.append("📊 Excel Report Generated:\n");
                body.append("• Sheet Name: ").append(sheetName).append("\n");
                body.append("• Candidates Processed: ").append(rows - 1).append("\n"); // Subtract header row
                body.append("• Columns: ").append(columns).append("\n");
                body.append("• File Size: ").append(excelData.get("fileSize").asLong() / 1024).append(" KB\n\n");
            }

            // Add download instructions
            body.append("📥 The Excel file with candidate rankings is ready for download.\n");
            body.append("You can access it from your workflow dashboard or use the direct download link below:\n\n");

            // Add direct download link if available
            if (excelData.has("webFileUrl")) {
                String downloadUrl = "http://localhost:8080" + excelData.get("webFileUrl").asText();
                body.append("🔗 Direct Download: ").append(downloadUrl).append("\n\n");
            }

            body.append("📁 File Location: ").append(excelData.get("outputFileUrl").asText()).append("\n");

        } catch (Exception e) {
            log.warn("Could not parse Excel data for email customization", e);
            body.append("📊 An Excel report with candidate rankings has been generated.\n");
            body.append("Please check your workflow dashboard for download options.\n\n");
        }

        body.append("✅ Best regards,\n");
        body.append("HR Workflow System\n");

        return body.toString();
    }
}