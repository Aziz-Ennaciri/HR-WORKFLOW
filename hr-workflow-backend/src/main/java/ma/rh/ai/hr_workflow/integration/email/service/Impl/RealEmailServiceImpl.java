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
    public String sendEmail(String configJson, String inputData) throws Exception {
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
}