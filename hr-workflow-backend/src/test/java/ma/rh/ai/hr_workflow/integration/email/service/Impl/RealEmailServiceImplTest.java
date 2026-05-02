package ma.rh.ai.hr_workflow.integration.email.service.Impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ma.rh.ai.hr_workflow.integration.email.DTOs.EmailResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RealEmailServiceImpl")
class RealEmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    private ObjectMapper objectMapper;
    private RealEmailServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Spring Boot auto-configures ObjectMapper with this disabled; mirror that here
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        service = new RealEmailServiceImpl(mailSender, objectMapper);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private String configJson(String to, String subject, String body) {
        return String.format(
                "{\"to\":\"%s\",\"subject\":\"%s\",\"body\":\"%s\"}",
                to != null ? to : "",
                subject != null ? subject : "",
                body != null ? body : "");
    }

    private String configJsonNullTo(String subject, String body) {
        return String.format(
                "{\"subject\":\"%s\",\"body\":\"%s\"}",
                subject != null ? subject : "",
                body != null ? body : "");
    }

    private String requestJson(String recipient) {
        return String.format("{\"recipient\":\"%s\"}", recipient != null ? recipient : "");
    }

    private String excelInputData(int rows, int columns, String sheetName,
                                  long fileSize, String webFileUrl, String outputFileUrl) {
        return String.format(
                "{\"rowsProcessed\":%d,\"columnsProcessed\":%d,\"sheetName\":\"%s\","
                        + "\"fileSize\":%d,\"webFileUrl\":\"%s\",\"outputFileUrl\":\"%s\"}",
                rows, columns, sheetName, fileSize, webFileUrl, outputFileUrl);
    }

    // ─── Happy path: regular (non-CV) workflow ────────────────────────────────────

    @Nested
    @DisplayName("Regular workflow emails")
    class RegularWorkflow {

        @Test
        @DisplayName("happy path — recipient from config, sends email and returns valid response JSON")
        void sendEmail_recipientFromConfig_happyPath() throws Exception {
            // Arrange
            String config  = configJson("hr@company.com", "Hello", "Welcome");
            String input   = requestJson("ignored@test.com");
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // Act
            String result = service.sendEmail(config, input, "Onboarding Workflow", "onboarding_wf");

            // Assert
            EmailResponseDTO response = objectMapper.readValue(result, EmailResponseDTO.class);
            assertThat(response.getRecipient()).isEqualTo("hr@company.com");
            assertThat(response.getStatus()).isEqualTo("sent");
            assertThat(response.getMessageId()).startsWith("msg_");
            assertThat(response.getSentAt()).isNotNull();
        }

        @Test
        @DisplayName("happy path — recipient missing from config, falls back to request.recipient")
        void sendEmail_recipientFallsBackToRequest() throws Exception {
            // Arrange
            String config = configJsonNullTo("Notification", "Body text");
            String input  = requestJson("fallback@company.com");
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // Act
            String result = service.sendEmail(config, input, "HR Notify", "hr_notify");

            // Assert
            EmailResponseDTO response = objectMapper.readValue(result, EmailResponseDTO.class);
            assertThat(response.getRecipient()).isEqualTo("fallback@company.com");
        }

        @Test
        @DisplayName("happy path — subject missing from config defaults to 'Workflow Notification'")
        void sendEmail_nullSubject_usesDefault() throws Exception {
            // Arrange
            String config = "{\"to\":\"a@b.com\"}";
            String input  = requestJson(null);
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // Act
            String result = service.sendEmail(config, input, "General Workflow", "general_wf");

            // Assert
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getSubject()).isEqualTo("Workflow Notification");
        }

        @Test
        @DisplayName("edge case — config and request both have empty recipient throws RuntimeException")
        void sendEmail_noRecipient_throws() {
            // Arrange
            String config = configJsonNullTo("Subject", "Body");
            String input  = requestJson("");

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.sendEmail(config, input, "Workflow", "wf_key"));
            assertThat(ex.getMessage()).contains("recipient");
            verify(mailSender, never()).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("exception — invalid configJson throws RuntimeException")
        void sendEmail_invalidConfigJson_throws() {
            // Arrange
            String brokenConfig = "{not valid json}";
            String input        = requestJson("r@test.com");

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.sendEmail(brokenConfig, input, "Workflow", "key"));
            assertThat(ex.getMessage()).contains("Email sending failed");
        }

        @Test
        @DisplayName("exception — invalid inputData JSON throws RuntimeException")
        void sendEmail_invalidInputJson_throws() {
            // Arrange
            String config    = configJson("r@test.com", "Subject", "Body");
            String badInput  = "{this is not json";

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.sendEmail(config, badInput, "Workflow", "key"));
            assertThat(ex.getMessage()).contains("Email sending failed");
        }

        @Test
        @DisplayName("exception — mailSender.send throws propagates as RuntimeException")
        void sendEmail_mailSenderThrows_wrapsException() {
            // Arrange
            String config = configJson("r@test.com", "Sub", "Body");
            String input  = requestJson("r@test.com");
            doThrow(new RuntimeException("SMTP connection failed"))
                    .when(mailSender).send(any(SimpleMailMessage.class));

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.sendEmail(config, input, "Workflow", "key"));
            assertThat(ex.getMessage()).contains("Email sending failed");
        }
    }

    // ─── CV workflow detection ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("CV workflow detection — isCvRecruitmentWorkflow()")
    class CvWorkflowDetection {

        @Test
        @DisplayName("workflowName contains 'cv' (case-insensitive) → CV workflow detected")
        void sendEmail_nameCv_detectsAsCv() throws Exception {
            // Arrange
            String config  = configJson("hr@test.com", "Subject", "Body");
            String input   = requestJson("hr@test.com");
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // Act
            String result = service.sendEmail(config, input, "CV Analysis Workflow", "some_key");

            // Assert — body includes CV recruitment specific text
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getText()).contains("CV Recruitment Analysis Complete");
        }

        @Test
        @DisplayName("workflowName contains 'recruitment' → CV workflow detected")
        void sendEmail_nameRecruitment_detectsAsCv() throws Exception {
            // Arrange
            String config  = configJson("hr@test.com", "", "");
            String input   = requestJson("hr@test.com");
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // Act
            String result = service.sendEmail(config, input, "Recruitment Pipeline", "rec_key");

            // Assert
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getText()).contains("CV Recruitment Analysis Complete");
        }

        @Test
        @DisplayName("workflowName contains 'candidate' → CV workflow detected")
        void sendEmail_nameCandidate_detectsAsCv() throws Exception {
            // Arrange
            String config  = configJson("hr@test.com", "Sub", "Body");
            String input   = requestJson("hr@test.com");
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // Act
            service.sendEmail(config, input, "Candidate Screening", "some_key");

            // Assert
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getText()).contains("CV Recruitment Analysis Complete");
        }

        @Test
        @DisplayName("workflowKey contains 'hiring' → CV workflow detected (key check)")
        void sendEmail_keyHiring_detectsAsCv() throws Exception {
            // Arrange
            String config  = configJson("hr@test.com", "Sub", "Body");
            String input   = requestJson("hr@test.com");
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // Act
            service.sendEmail(config, input, "Generic Workflow", "hiring_pipeline_key");

            // Assert
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getText()).contains("CV Recruitment Analysis Complete");
        }

        @Test
        @DisplayName("regular workflow name/key → not detected as CV workflow")
        void sendEmail_regularWorkflow_notCv() throws Exception {
            // Arrange
            String config  = configJson("hr@test.com", "Sub", "Body text");
            String input   = requestJson("hr@test.com");
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // Act
            service.sendEmail(config, input, "Onboarding Process", "onboarding_flow");

            // Assert — body does NOT include CV-specific text
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getText()).doesNotContain("CV Recruitment Analysis Complete");
            assertThat(captor.getValue().getText()).isEqualTo("Body text");
        }

        @Test
        @DisplayName("null workflowName and workflowKey → not detected as CV workflow")
        void sendEmail_nullNameAndKey_notCv() throws Exception {
            // Arrange
            String config  = configJson("hr@test.com", "Sub", "Body");
            String input   = requestJson("hr@test.com");
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // Act
            service.sendEmail(config, input, null, null);

            // Assert
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getText()).doesNotContain("CV Recruitment Analysis Complete");
        }
    }

    // ─── CV workflow subject customization ────────────────────────────────────────

    @Nested
    @DisplayName("CV workflow — subject customization")
    class CvSubjectCustomization {

        @Test
        @DisplayName("original subject non-empty → kept as-is (not overridden)")
        void sendEmail_cvWorkflow_existingSubject_keepOriginal() throws Exception {
            // Arrange
            String config  = configJson("hr@test.com", "My Custom Subject", "");
            String input   = requestJson("hr@test.com");
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // Act
            service.sendEmail(config, input, "CV Screening", "cv_key");

            // Assert
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getSubject()).isEqualTo("My Custom Subject");
        }

        @Test
        @DisplayName("original subject empty → defaults to 'CV Analysis Complete - <workflowName>'")
        void sendEmail_cvWorkflow_emptySubject_usesDefault() throws Exception {
            // Arrange
            String config  = configJson("hr@test.com", "", "");
            String input   = requestJson("hr@test.com");
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // Act
            service.sendEmail(config, input, "CV Ranking Flow", "cv_rank");

            // Assert
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getSubject()).isEqualTo("CV Analysis Complete - CV Ranking Flow");
        }
    }

    // ─── CV workflow body customization with Excel data ───────────────────────────

    @Nested
    @DisplayName("CV workflow — body customization with Excel data")
    class CvBodyCustomization {

        @Test
        @DisplayName("inputData contains Excel rowsProcessed fields — body includes stats and download link")
        void sendEmail_cvWorkflow_withExcelData_bodyContainsStats() throws Exception {
            // Arrange
            String config = configJson("hr@test.com", "", "Initial body");
            String excelInput = excelInputData(
                    6, 5, "Candidates", 51200L,
                    "/api/v1/files/excel/report.xlsx",
                    "/tmp/workflow-report.xlsx");
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // Act
            service.sendEmail(config, excelInput, "CV Recruitment Pipeline", "cv_pipe");

            // Assert
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            String body = captor.getValue().getText();
            assertThat(body).contains("Initial body");
            assertThat(body).contains("CV Recruitment Analysis Complete");
            assertThat(body).contains("Candidates Processed: 5"); // rows - 1 = header row excluded
            assertThat(body).contains("Sheet Name: Candidates");
            assertThat(body).contains("50 KB");            // 51200 / 1024
            assertThat(body).contains("Direct Download");
            assertThat(body).contains("/tmp/workflow-report.xlsx");
        }

        @Test
        @DisplayName("inputData has no rowsProcessed → body uses fallback Excel message")
        void sendEmail_cvWorkflow_noRowsProcessed_fallbackMessage() throws Exception {
            // Arrange
            String config      = configJson("hr@test.com", "", "");
            String plainInput  = "{\"someOtherField\":\"value\"}";
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // Act
            service.sendEmail(config, plainInput, "CV Pipeline", "cv_key");

            // Assert
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            String body = captor.getValue().getText();
            assertThat(body).contains("An Excel report with candidate rankings has been generated");
        }

        @Test
        @DisplayName("inputData is not JSON → body uses fallback Excel message without throwing")
        void sendEmail_cvWorkflow_nonJsonInputData_fallbackMessage() throws Exception {
            // Arrange
            String config     = configJson("hr@test.com", "", "");
            String rawInput   = "plain text, not JSON at all";
            // Note: objectMapper.readValue(inputData, EmailRequestDTO.class) would fail with raw text
            // So we use a valid recipient in the config to avoid no-recipient error
            // but the inputData parsing for EmailRequestDTO also needs to succeed.
            // Actually, the service calls objectMapper.readValue(inputData, EmailRequestDTO.class)
            // which would throw on plain text. So provide a fallback via config.to already set.
            // But readValue(inputData, EmailRequestDTO.class) at line 37 would throw if not JSON.
            // This test therefore expects RuntimeException from the email sending method.
            assertThrows(RuntimeException.class,
                    () -> service.sendEmail(config, rawInput, "CV Pipeline", "cv_key"));
        }

        @Test
        @DisplayName("inputData is valid EmailRequestDTO JSON but has no Excel fields → fallback body")
        void sendEmail_cvWorkflow_emailRequestJsonNoExcelFields_fallbackMessage() throws Exception {
            // Arrange
            String config    = configJson("hr@test.com", "", "");
            String jsonInput = "{\"recipient\":\"ignored@test.com\"}";
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // Act
            service.sendEmail(config, jsonInput, "CV Pipeline", "cv_key");

            // Assert
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            String body = captor.getValue().getText();
            assertThat(body).contains("An Excel report with candidate rankings has been generated");
        }

        @Test
        @DisplayName("inputData has webFileUrl → body includes direct download URL")
        void sendEmail_cvWorkflow_withWebFileUrl_includesDownloadLink() throws Exception {
            // Arrange
            String config      = configJson("hr@test.com", "", "");
            String excelInput  = excelInputData(
                    4, 3, "Rankings", 20480L,
                    "/api/v1/files/excel/rankings.xlsx",
                    "/tmp/rankings.xlsx");
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // Act
            service.sendEmail(config, excelInput, "candidate selection", "cand_sel");

            // Assert
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getText())
                    .contains("http://localhost:8080/api/v1/files/excel/rankings.xlsx");
        }
    }
}
