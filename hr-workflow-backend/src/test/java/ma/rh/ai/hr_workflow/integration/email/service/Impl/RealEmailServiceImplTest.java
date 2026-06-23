package ma.rh.ai.hr_workflow.integration.email.service.Impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.mail.internet.MimeMessage;
import ma.rh.ai.hr_workflow.integration.email.DTOs.EmailResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        service = new RealEmailServiceImpl(mailSender, objectMapper);
    }


    private MimeMessage newMessage() throws Exception {
        return new MimeMessage(jakarta.mail.Session.getInstance(new Properties()));
    }

    private void setupSuccessfulSend(MimeMessage message) {
        when(mailSender.createMimeMessage()).thenReturn(message);
        doNothing().when(mailSender).send(any(MimeMessage.class));
    }

    private String configJson(String to) {
        return "{\"to\":\"" + to + "\"}";
    }

    private String readHtml(MimeMessage msg) throws Exception {
        return (String) msg.getContent();
    }


    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("sends MimeMessage (HTML), not SimpleMailMessage")
        void sendEmail_usesMimeMessage() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            service.sendEmail(configJson("hr@company.com"), "some content", "Onboarding", "onboarding");

            verify(mailSender).createMimeMessage();
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("returns valid response JSON with recipient, status=sent, messageId, sentAt")
        void sendEmail_returnsValidResponseJson() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            String result = service.sendEmail(configJson("hr@company.com"), "output", "My Workflow", "my-wf");

            EmailResponseDTO response = objectMapper.readValue(result, EmailResponseDTO.class);
            assertThat(response.getRecipient()).isEqualTo("hr@company.com");
            assertThat(response.getStatus()).isEqualTo("sent");
            assertThat(response.getMessageId()).startsWith("msg_");
            assertThat(response.getSentAt()).isNotNull();
        }

        @Test
        @DisplayName("subject is always '[workflowName] — Workflow Results'")
        void sendEmail_subjectFormat() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            service.sendEmail(configJson("hr@company.com"), "data", "CV Candidate Review", "cv-review");

            assertThat(message.getSubject()).isEqualTo("CV Candidate Review — Workflow Results");
        }

        @Test
        @DisplayName("email body is valid HTML (starts with DOCTYPE, contains html tags)")
        void sendEmail_bodyIsHtml() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            service.sendEmail(configJson("hr@company.com"), "data", "Workflow", "wf");

            message.saveChanges();
            assertThat(message.getContentType()).containsIgnoringCase("text/html");
            String html = readHtml(message);
            assertThat(html).startsWith("<!DOCTYPE html>");
            assertThat(html).contains("<body");
        }

        @Test
        @DisplayName("null workflowName falls back to 'Workflow' in subject")
        void sendEmail_nullWorkflowName_fallback() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            service.sendEmail(configJson("hr@company.com"), "data", null, null);

            assertThat(message.getSubject()).isEqualTo("Workflow — Workflow Results");
        }
    }


    @Nested
    @DisplayName("File-output node — download button template")
    class FileOutputContent {

        private String excelResponse(int rows, String webFileUrl) {
            return String.format(
                "{\"rowsProcessed\":%d,\"columnsProcessed\":5,\"outputFileUrl\":\"/tmp/f.xlsx\","
                    + "\"webFileUrl\":\"%s\",\"fileSize\":20480,\"sheetName\":\"Data\","
                    + "\"operation\":\"WRITE\",\"fileContent\":\"AAABBBCCC\"}",
                rows, webFileUrl);
        }

        @Test
        @DisplayName("webFileUrl present → email shows 'Hello,' greeting and download button")
        void fileOutput_showsDownloadButton() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            service.sendEmail(configJson("hr@company.com"),
                excelResponse(6, "/api/v1/files/excel/report.xlsx"),
                "CV Review", "cv-review");

            String html = readHtml(message);
            assertThat(html).contains("Hello,");
            assertThat(html).contains("completed successfully");
            assertThat(html).contains("Download Report");
            assertThat(html).contains("http://localhost:8080/api/v1/files/excel/report.xlsx");
        }

        @Test
        @DisplayName("download button has dark-blue background #1a365d and no underline")
        void fileOutput_buttonStyling() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            service.sendEmail(configJson("hr@company.com"),
                excelResponse(4, "/api/v1/files/excel/r.xlsx"),
                "Report Workflow", "rw");

            String html = readHtml(message);
            assertThat(html).contains("background:#1a365d");
            assertThat(html).contains("text-decoration:none");
            assertThat(html).contains("border-radius:6px");
            assertThat(html).contains("padding:12px 24px");
        }

        @Test
        @DisplayName("rowsProcessed=6 → shows '5 candidates processed' (header row excluded)")
        void fileOutput_showsCandidateCount() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            service.sendEmail(configJson("hr@company.com"),
                excelResponse(6, "/api/v1/files/excel/r.xlsx"),
                "Screening", "s");

            String html = readHtml(message);
            assertThat(html).contains("5");
            assertThat(html).contains("candidates processed");
        }

        @Test
        @DisplayName("rowsProcessed=1 (only header) → no candidate count line shown")
        void fileOutput_onlyHeader_noCountLine() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            service.sendEmail(configJson("hr@company.com"),
                excelResponse(1, "/api/v1/files/excel/r.xlsx"),
                "Empty Report", "er");

            String html = readHtml(message);
            assertThat(html).doesNotContain("candidates processed");
        }

        @Test
        @DisplayName("file-output email does NOT expose technical fields in body")
        void fileOutput_technicalFields_notInEmail() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            service.sendEmail(configJson("hr@company.com"),
                excelResponse(4, "/api/v1/files/excel/r.xlsx"),
                "Report", "r");

            String html = readHtml(message);
            assertThat(html).doesNotContain("/tmp/f.xlsx");
            assertThat(html).doesNotContain("AAABBBCCC");
            assertThat(html).doesNotContain("fileContent");
            assertThat(html).doesNotContain("outputFileUrl");
        }
    }


    @Nested
    @DisplayName("Content sanitization — technical fields hidden")
    class ContentSanitization {

        @Test
        @DisplayName("HIDDEN_FIELDS removed from JSON object before rendering")
        void jsonObject_technicalFieldsStripped() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);
            String input = "{\"candidateName\":\"Alice\",\"score\":\"92\","
                + "\"fileSize\":20480,\"operation\":\"WRITE\","
                + "\"createdAt\":\"2025-01-01\",\"outputFileUrl\":\"/tmp/x.xlsx\"}";

            service.sendEmail(configJson("hr@company.com"), input, "Review", "r");

            String html = readHtml(message);
            assertThat(html).contains("Alice");
            assertThat(html).contains("92");
            assertThat(html).doesNotContain("fileSize");
            assertThat(html).doesNotContain("20480");
            assertThat(html).doesNotContain("operation");
            assertThat(html).doesNotContain("WRITE");
            assertThat(html).doesNotContain("outputFileUrl");
            assertThat(html).doesNotContain("/tmp/x.xlsx");
        }

        @Test
        @DisplayName("base64-like string (>200 chars, no spaces) stripped from email")
        void jsonObject_base64ValueStripped() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);
            String base64 = "A".repeat(250); // long, no spaces
            String input = "{\"name\":\"Bob\",\"fileContent\":\"" + base64 + "\"}";

            service.sendEmail(configJson("hr@company.com"), input, "Review", "r");

            String html = readHtml(message);
            assertThat(html).contains("Bob");
            assertThat(html).doesNotContain(base64);
        }

        @Test
        @DisplayName("short string values are kept even if they look technical")
        void jsonObject_shortValues_kept() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);
            String input = "{\"status\":\"completed\",\"ref\":\"REF-001\"}";

            service.sendEmail(configJson("hr@company.com"), input, "Review", "r");

            String html = readHtml(message);
            assertThat(html).contains("completed");
            assertThat(html).contains("REF-001");
        }

        @Test
        @DisplayName("technical fields stripped from each element in JSON array")
        void jsonArray_technicalFieldsStrippedPerRow() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);
            String input = "[{\"name\":\"Alice\",\"sheetName\":\"Sheet1\",\"score\":\"90\"},"
                + "{\"name\":\"Bob\",\"sheetName\":\"Sheet1\",\"score\":\"85\"}]";

            service.sendEmail(configJson("hr@company.com"), input, "CV Ranking", "cv");

            String html = readHtml(message);
            assertThat(html).contains("Alice");
            assertThat(html).contains("Bob");
            assertThat(html).doesNotContain("sheetName");
            assertThat(html).doesNotContain("Sheet1");
        }
    }


    @Nested
    @DisplayName("Content rendering")
    class ContentRendering {

        @Test
        @DisplayName("null inputData → HTML contains fallback message")
        void sendEmail_nullContent_fallback() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            service.sendEmail(configJson("hr@company.com"), null, "Workflow", "wf");

            String html = readHtml(message);
            assertThat(html).contains("No output data available");
        }

        @Test
        @DisplayName("blank inputData → HTML contains fallback message")
        void sendEmail_blankContent_fallback() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            service.sendEmail(configJson("hr@company.com"), "   ", "Workflow", "wf");

            String html = readHtml(message);
            assertThat(html).contains("No output data available");
        }

        @Test
        @DisplayName("JSON array of objects → HTML contains <table> and auto-detected headers")
        void sendEmail_jsonArray_rendersTable() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);
            String jsonArray = "[{\"candidateName\":\"Alice\",\"score\":\"95\"},"
                + "{\"candidateName\":\"Bob\",\"score\":\"88\"}]";

            service.sendEmail(configJson("hr@company.com"), jsonArray, "CV Review", "cv");

            String html = readHtml(message);
            assertThat(html).contains("<table");
            assertThat(html).contains("<thead>");
            assertThat(html).contains("Candidate Name");
            assertThat(html).contains("Alice");
            assertThat(html).contains("Bob");
        }

        @Test
        @DisplayName("JSON object → HTML contains key-value table rows")
        void sendEmail_jsonObject_rendersKeyValue() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);
            String jsonObj = "{\"department\":\"Engineering\",\"headcount\":\"42\"}";

            service.sendEmail(configJson("hr@company.com"), jsonObj, "Report", "report");

            String html = readHtml(message);
            assertThat(html).contains("Department");
            assertThat(html).contains("Engineering");
            assertThat(html).contains("Headcount");
            assertThat(html).contains("42");
        }

        @Test
        @DisplayName("plain text → HTML contains <pre> with white-space:pre-wrap")
        void sendEmail_plainText_rendersPre() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            service.sendEmail(configJson("hr@company.com"),
                "Candidate accepted. Start date: 2025-09-01.", "Policy Check", "policy");

            String html = readHtml(message);
            assertThat(html).contains("<pre");
            assertThat(html).contains("pre-wrap");
            assertThat(html).contains("Candidate accepted.");
        }

        @Test
        @DisplayName("markdown JSON code fence → stripped before parsing, renders as table")
        void sendEmail_markdownCodeFence_strippedAndParsed() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);
            String fenced = "```json\n[{\"name\":\"Alice\",\"role\":\"Dev\"}]\n```";

            service.sendEmail(configJson("hr@company.com"), fenced, "Workflow", "wf");

            String html = readHtml(message);
            assertThat(html).contains("<table");
            assertThat(html).contains("Alice");
            assertThat(html).doesNotContain("```");
        }

        @Test
        @DisplayName("single-key wrapper JSON {\"result\":\"text\"} → unwraps and renders inner value")
        void sendEmail_singleKeyWrapper_unwrapsInner() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);
            String wrapped = "{\"result\":\"Candidate screening passed.\"}";

            service.sendEmail(configJson("hr@company.com"), wrapped, "Screening", "screen");

            String html = readHtml(message);
            assertThat(html).contains("Candidate screening passed.");
        }

        @Test
        @DisplayName("HTML in workflow name is escaped to prevent injection")
        void sendEmail_workflowNameWithHtml_escaped() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            service.sendEmail(configJson("hr@company.com"), "data", "<script>alert(1)</script>", "wf");

            String html = readHtml(message);
            assertThat(html).doesNotContain("<script>");
            assertThat(html).contains("&lt;script&gt;");
        }

        @Test
        @DisplayName("HTML email contains header with #1a365d background and workflow name subtitle")
        void sendEmail_html_containsHeaderSection() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            service.sendEmail(configJson("hr@company.com"), "output", "Document Review", "doc-review");

            String html = readHtml(message);
            assertThat(html).contains("#1a365d");
            assertThat(html).contains("HR Workflow System");
            assertThat(html).contains("Document Review");
        }

        @Test
        @DisplayName("HTML email contains footer with best regards and disclaimer")
        void sendEmail_html_containsFooterSection() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            service.sendEmail(configJson("hr@company.com"), "output", "Workflow", "wf");

            String html = readHtml(message);
            assertThat(html).contains("Best regards");
            assertThat(html).contains("automated notification");
            assertThat(html).contains("#f7f7f7");
        }

        @Test
        @DisplayName("alternating row colors applied: #f8f9fa for even rows")
        void sendEmail_jsonArray_alternatingRowColors() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);
            String array = "[{\"x\":\"1\"},{\"x\":\"2\"},{\"x\":\"3\"}]";

            service.sendEmail(configJson("hr@company.com"), array, "Workflow", "wf");

            String html = readHtml(message);
            assertThat(html).contains("#f8f9fa");
        }
    }


    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("missing recipient in config → throws RuntimeException mentioning 'recipient'")
        void sendEmail_missingRecipient_throws() {
            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.sendEmail("{}", "data", "Workflow", "wf"));
            assertThat(ex.getMessage()).contains("recipient");
        }

        @Test
        @DisplayName("empty recipient in config → throws RuntimeException")
        void sendEmail_emptyRecipient_throws() {
            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.sendEmail("{\"to\":\"\"}", "data", "Workflow", "wf"));
            assertThat(ex.getMessage()).contains("Email sending failed");
        }

        @Test
        @DisplayName("invalid configJson → throws RuntimeException containing 'Email sending failed'")
        void sendEmail_invalidConfigJson_throws() {
            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.sendEmail("{not valid json}", "data", "Workflow", "wf"));
            assertThat(ex.getMessage()).contains("Email sending failed");
        }

        @Test
        @DisplayName("mailSender.send() throws → wrapped as RuntimeException with 'Email sending failed'")
        void sendEmail_mailSenderThrows_wrapsException() throws Exception {
            MimeMessage message = newMessage();
            when(mailSender.createMimeMessage()).thenReturn(message);
            doThrow(new RuntimeException("SMTP connection refused"))
                .when(mailSender).send(any(MimeMessage.class));

            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.sendEmail(configJson("hr@company.com"), "data", "Workflow", "wf"));
            assertThat(ex.getMessage()).contains("Email sending failed");
        }

        @Test
        @DisplayName("invalid JSON inputData is NOT an error — rendered as plain text")
        void sendEmail_invalidInputJson_renderedAsPlainText() throws Exception {
            MimeMessage message = newMessage();
            setupSuccessfulSend(message);

            String result = service.sendEmail(
                configJson("hr@company.com"), "{this is not json}", "Workflow", "wf");

            EmailResponseDTO response = objectMapper.readValue(result, EmailResponseDTO.class);
            assertThat(response.getStatus()).isEqualTo("sent");
            assertThat(readHtml(message)).contains("<pre");
        }
    }
}
