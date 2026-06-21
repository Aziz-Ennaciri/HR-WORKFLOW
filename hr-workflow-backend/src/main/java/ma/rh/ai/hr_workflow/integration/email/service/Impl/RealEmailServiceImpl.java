package ma.rh.ai.hr_workflow.integration.email.service.Impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.integration.email.DTOs.EmailConfigDTO;
import ma.rh.ai.hr_workflow.integration.email.DTOs.EmailResponseDTO;
import ma.rh.ai.hr_workflow.integration.email.service.EmailService;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class RealEmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;

    // Fields that are internal/technical and must never appear in emails
    private static final Set<String> HIDDEN_FIELDS = Set.of(
        "fileContent", "outputFileUrl", "filePath", "fileSize",
        "createdAt", "operation", "sheetName", "columnsProcessed"
    );

    @Override
    public String sendEmail(String configJson, String inputData, String workflowName, String workflowKey) {
        try {
            log.info("Sending HTML email for workflow: {}", workflowName);

            EmailConfigDTO config = objectMapper.readValue(configJson, EmailConfigDTO.class);

            String to = config.getTo();
            if (to == null || to.isEmpty()) {
                throw new RuntimeException("Email recipient is required");
            }

            String name = workflowName != null ? workflowName : "Workflow";
            String subject = name + " — Workflow Results";
            String htmlBody = buildHtmlEmail(name, inputData);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);

            log.info("Email sent successfully to: {}", to);

            EmailResponseDTO response = new EmailResponseDTO();
            response.setMessageId("msg_" + UUID.randomUUID().toString().substring(0, 8));
            response.setRecipient(to);
            response.setStatus("sent");
            response.setSentAt(LocalDateTime.now());

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("Failed to send email", e);
            throw new RuntimeException("Email sending failed: " + e.getMessage());
        }
    }

    // ── Template orchestration ────────────────────────────────────────────────────

    private String buildHtmlEmail(String workflowName, String rawContent) {
        String safeName = escapeHtml(workflowName);
        String bodyContent = resolveBodyContent(rawContent, safeName);

        return "<!DOCTYPE html>"
            + "<html><head><meta charset=\"UTF-8\"></head>"
            + "<body style=\"margin:0;padding:0;background:#f4f6f8;font-family:Arial,sans-serif;\">"
            + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f6f8;\">"
            + "<tr><td align=\"center\" style=\"padding:30px 0;\">"
            + "<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" "
            +   "style=\"background:#ffffff;border-radius:8px;overflow:hidden;"
            +   "box-shadow:0 2px 8px rgba(0,0,0,0.1);\">"

            // Header
            + "<tr><td style=\"background:#1a365d;padding:32px 40px;\">"
            + "<p style=\"margin:0;font-size:22px;font-weight:bold;color:#ffffff;"
            +   "letter-spacing:1px;\">HR Workflow System</p>"
            + "<p style=\"margin:8px 0 0;font-size:14px;color:#90cdf4;\">" + safeName + "</p>"
            + "</td></tr>"

            // Body
            + "<tr><td style=\"padding:32px 40px;background:#ffffff;\">"
            + bodyContent
            + "</td></tr>"

            // Footer
            + "<tr><td style=\"background:#f7f7f7;padding:24px 40px;"
            +   "border-top:1px solid #e2e8f0;\">"
            + "<p style=\"margin:0;font-size:14px;color:#4a5568;\">Best regards,"
            +   "<br><strong>HR Workflow System</strong></p>"
            + "<p style=\"margin:12px 0 0;font-size:12px;color:#6b7280;\">This is an automated "
            +   "notification from HR Workflow System.</p>"
            + "</td></tr>"

            + "</table></td></tr></table>"
            + "</body></html>";
    }

    private String resolveBodyContent(String rawContent, String safeName) {
        if (rawContent == null || rawContent.isBlank()) {
            return "<p style=\"margin:0 0 12px;font-size:15px;color:#2d3748;\">Hello,</p>"
                + "<p style=\"font-size:14px;color:#4a5568;\">Workflow completed successfully. "
                + "No output data available.</p>";
        }

        String stripped = stripCodeFences(rawContent.trim());

        try {
            JsonNode node = objectMapper.readTree(stripped);

            // File-output node (Excel/CSV result) — show download-button template
            if (node.isObject() && node.has("webFileUrl")) {
                return buildFileOutputContent(node, safeName);
            }

            // Regular JSON — scrub technical fields and render with standard intro
            String sanitized = sanitizeForEmail(node);
            return standardIntro(safeName) + renderContent(sanitized);

        } catch (Exception e) {
            return standardIntro(safeName) + renderAsPlainText(stripped);
        }
    }

    // ── File-output content (download button) ─────────────────────────────────────

    private String buildFileOutputContent(JsonNode node, String safeName) {
        String downloadUrl = "http://localhost:8080" + node.get("webFileUrl").asText();

        String countLine = "";
        if (node.has("rowsProcessed")) {
            int candidates = Math.max(0, node.get("rowsProcessed").asInt() - 1);
            if (candidates > 0) {
                countLine = "<p style=\"margin:16px 0;font-size:15px;color:#2d3748;\">"
                    + "&#128202; <strong>" + candidates + "</strong> candidates processed</p>";
            }
        }

        return "<p style=\"margin:0 0 12px;font-size:15px;color:#2d3748;\">Hello,</p>"
            + "<p style=\"margin:0 0 16px;font-size:15px;color:#2d3748;\">Your workflow "
            + "<strong>" + safeName + "</strong> has completed successfully.</p>"
            + countLine
            + "<p style=\"margin:24px 0 0;\">"
            + "<a href=\"" + escapeHtml(downloadUrl) + "\" "
            + "style=\"display:inline-block;background:#1a365d;color:#ffffff;"
            + "text-decoration:none;padding:12px 24px;border-radius:6px;"
            + "font-size:15px;font-weight:bold;letter-spacing:0.5px;\">"
            + "Download Report</a>"
            + "</p>";
    }

    // ── JSON sanitization ─────────────────────────────────────────────────────────

    private String sanitizeForEmail(JsonNode node) throws Exception {
        if (node.isArray()) {
            var arr = objectMapper.createArrayNode();
            node.forEach(elem -> arr.add(elem.isObject() ? cleanObject((ObjectNode) elem) : elem));
            return objectMapper.writeValueAsString(arr);
        }
        if (node.isObject()) {
            return objectMapper.writeValueAsString(cleanObject((ObjectNode) node));
        }
        return node.toString();
    }

    private ObjectNode cleanObject(ObjectNode obj) {
        ObjectNode result = objectMapper.createObjectNode();
        obj.fields().forEachRemaining(entry -> {
            if (!HIDDEN_FIELDS.contains(entry.getKey()) && !isTechnicalValue(entry.getValue())) {
                result.set(entry.getKey(), entry.getValue());
            }
        });
        return result;
    }

    private boolean isTechnicalValue(JsonNode value) {
        if (!value.isTextual()) return false;
        String text = value.asText();
        return text.length() > 200 && text.chars().noneMatch(Character::isWhitespace);
    }

    // ── Content detection & rendering ─────────────────────────────────────────────

    private String renderContent(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return "<p style=\"font-size:14px;color:#4a5568;\">"
                + "Workflow completed successfully. No output data available.</p>";
        }

        String content = stripCodeFences(rawContent.trim());

        try {
            JsonNode node = objectMapper.readTree(content);

            if (node.isArray() && node.size() > 0 && node.get(0).isObject()) {
                return renderAsTable(node);
            }

            if (node.isObject()) {
                // Unwrap single-key wrapper objects like {"result": "..."}
                if (node.size() == 1) {
                    JsonNode inner = node.fields().next().getValue();
                    if (inner.isTextual() || inner.isArray() || inner.isObject()) {
                        return renderContent(inner.isTextual() ? inner.asText() : inner.toString());
                    }
                }
                return renderAsKeyValue(node);
            }

            return renderAsPlainText(node.asText());

        } catch (Exception e) {
            return renderAsPlainText(content);
        }
    }

    private String renderAsTable(JsonNode array) {
        List<String> headers = new ArrayList<>();
        array.get(0).fieldNames().forEachRemaining(headers::add);

        StringBuilder sb = new StringBuilder();
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"border-collapse:collapse;font-size:14px;\"><thead><tr>");

        for (String h : headers) {
            sb.append("<th style=\"background:#2d3748;color:#ffffff;padding:10px 12px;"
                + "text-align:left;font-weight:bold;border:1px solid #4a5568;\">")
              .append(escapeHtml(formatKey(h))).append("</th>");
        }
        sb.append("</tr></thead><tbody>");

        for (int i = 0; i < array.size(); i++) {
            JsonNode row = array.get(i);
            String bg = (i % 2 == 0) ? "#f8f9fa" : "#ffffff";
            sb.append("<tr style=\"background:").append(bg).append(";\">");
            for (String h : headers) {
                String val = row.has(h) ? nodeToString(row.get(h)) : "";
                sb.append("<td style=\"padding:10px 12px;border:1px solid #e2e8f0;color:#2d3748;\">")
                  .append(escapeHtml(val)).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private String renderAsKeyValue(JsonNode obj) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"border-collapse:collapse;font-size:14px;\">");

        Iterator<String> fields = obj.fieldNames();
        int i = 0;
        while (fields.hasNext()) {
            String key = fields.next();
            String val = nodeToString(obj.get(key));
            String bg = (i % 2 == 0) ? "#f8f9fa" : "#ffffff";
            sb.append("<tr style=\"background:").append(bg).append(";\">"
                + "<td style=\"padding:10px 12px;font-weight:bold;color:#2d3748;"
                + "border:1px solid #e2e8f0;width:35%;\">")
              .append(escapeHtml(formatKey(key))).append("</td>"
                + "<td style=\"padding:10px 12px;color:#4a5568;border:1px solid #e2e8f0;\">")
              .append(escapeHtml(val)).append("</td></tr>");
            i++;
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String renderAsPlainText(String text) {
        return "<pre style=\"font-size:14px;line-height:1.6;color:#2d3748;white-space:pre-wrap;"
            + "background:#f8f9fa;padding:16px;border-radius:4px;border:1px solid #e2e8f0;"
            + "overflow-x:auto;font-family:monospace;\">"
            + escapeHtml(text) + "</pre>";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private String stripCodeFences(String content) {
        return content
            .replaceAll("(?s)^```(?:json)?\\s*", "")
            .replaceAll("(?s)```\\s*$", "")
            .trim();
    }

    private String standardIntro(String safeName) {
        return "<p style=\"margin:0 0 20px;font-size:15px;color:#2d3748;\">Your workflow "
            + "<strong>" + safeName + "</strong> has completed. Here are the results:</p>";
    }

    private String nodeToString(JsonNode value) {
        if (value == null || value.isNull()) return "";
        if (value.isTextual()) return value.asText();
        return value.toString();
    }

    private String formatKey(String key) {
        String spaced = key
            .replaceAll("([a-z])([A-Z])", "$1 $2")
            .replace("_", " ")
            .trim();
        if (spaced.isEmpty()) return key;
        StringBuilder result = new StringBuilder();
        for (String word : spaced.split(" ")) {
            if (!word.isEmpty()) {
                if (result.length() > 0) result.append(" ");
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return result.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
