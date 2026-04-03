package ma.rh.ai.hr_workflow.integration.excel.service.Impl;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.integration.excel.DTOs.ExcelConfigDTO;
import ma.rh.ai.hr_workflow.integration.excel.DTOs.ExcelResponseDTO;
import ma.rh.ai.hr_workflow.integration.excel.service.ExcelService;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class RealExcelServiceImpl implements ExcelService {

    private final ObjectMapper objectMapper;

    @Override
    public String processData(String configJson, String inputData) throws Exception {
        try {
            log.info("📊 Excel: Processing data...");

            ExcelConfigDTO config = objectMapper.readValue(configJson, ExcelConfigDTO.class);

            String sheetName = config.getSheetName() != null ? config.getSheetName() : "Data";
            String operation = config.getOperation() != null ? config.getOperation() : "WRITE";

            // Parse input data - try JSON first, fallback to string
            JsonNode dataNode = null;
            String textData = null;
            try {
                dataNode = objectMapper.readTree(inputData);
            } catch (Exception e) {
                // Not JSON, treat as text data (e.g., from GPT node)
                textData = inputData;
            }

            // Create workbook
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet(sheetName);

            int rowNum = 0;

            int columnsProcessed = 5; // Default for candidate table
            List<Object> dataList = null;

            if (dataNode != null) {
                // Check if this is GPT response with analysis field
                if (dataNode.has("analysis")) {
                    String analysisText = dataNode.get("analysis").asText();
                    try {
                        // Try to extract JSON array from analysis text
                        String jsonText = extractJsonFromText(analysisText);
                        JsonNode analysisNode = objectMapper.readTree(jsonText);
                        if (analysisNode.isArray()) {
                            log.info("📊 Excel: Found candidate array in analysis field");
                            rowNum = createCandidateArrayTable(workbook, sheet, analysisNode, rowNum);
                            dataList = objectMapper.convertValue(analysisNode, List.class);
                        } else {
                            // Fallback to regular JSON table
                            rowNum = createJsonTable(workbook, sheet, dataNode, rowNum);
                            columnsProcessed = dataNode.size();
                            dataList = objectMapper.convertValue(dataNode, List.class);
                        }
                    } catch (Exception e) {
                        // Analysis is not JSON, treat as text
                        log.warn("⚠️ Analysis field does not contain valid JSON, using text parsing. Raw analysis begins: {}", analysisText.substring(0, Math.min(200, analysisText.length())));

                        // Additional fallback: try to extract first JSON array from code-like output
                        String possibleJson = extractJsonFromText(analysisText);
                        boolean fallbackApplied = false;
                        if (possibleJson != null && !possibleJson.trim().isEmpty() && !possibleJson.trim().equals(analysisText.trim())) {
                            try {
                                JsonNode fallbackNode = objectMapper.readTree(possibleJson);
                                if (fallbackNode.isArray()) {
                                    log.info("📊 Excel: Fallback JSON array extracted from GPT code output");
                                    rowNum = createCandidateArrayTable(workbook, sheet, fallbackNode, rowNum);
                                    dataList = objectMapper.convertValue(fallbackNode, List.class);
                                    fallbackApplied = true;
                                }
                            } catch (Exception innerEx) {
                                log.warn("⚠️ Fallback JSON extraction failed: {}", innerEx.getMessage());
                            }
                        }

                        if (!fallbackApplied) {
                            List<CandidateData> candidates = parseCandidatesFromText(analysisText);
                            if (!candidates.isEmpty()) {
                                rowNum = createCandidateTable(workbook, sheet, candidates, rowNum);
                                dataList = candidates.stream().map(c -> Map.of(
                                        "name", c.name,
                                        "email", c.email,
                                        "experience", c.experience,
                                        "skills", c.skills,
                                        "score", c.score
                                )).collect(Collectors.toList());
                            } else {
                                rowNum = createJsonTable(workbook, sheet, dataNode, rowNum);
                                columnsProcessed = dataNode.size();
                                dataList = objectMapper.convertValue(dataNode, List.class);
                            }
                        }
                    }
                } else {
                    // Regular JSON data - create table
                    rowNum = createJsonTable(workbook, sheet, dataNode, rowNum);
                    columnsProcessed = dataNode.size();
                    dataList = objectMapper.convertValue(dataNode, List.class);
                }
            } else if (textData != null) {
                // Try to parse GPT candidate analysis
                List<CandidateData> candidates = parseCandidatesFromText(textData);

                if (!candidates.isEmpty()) {
                    log.info("📋 Parsed {} candidates from GPT output", candidates.size());
                    rowNum = createCandidateTable(workbook, sheet, candidates, rowNum);
                    dataList = candidates.stream().map(c -> Map.of(
                        "name", c.name,
                        "email", c.email,
                        "experience", c.experience,
                        "skills", c.skills,
                        "score", c.score
                    )).collect(Collectors.toList());
                } else {
                    // Fallback: Plain text formatting
                    log.warn("⚠️ Could not parse candidates, using plain text format");
                    rowNum = createPlainTextSheet(workbook, sheet, textData, rowNum);
                    columnsProcessed = 1;
                }
            }

            // Save to temp file
            File tempFile = File.createTempFile("workflow-", ".xlsx");
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                workbook.write(outputStream);
            }
            workbook.close();

            long fileSize = tempFile.length();
            String filePath = tempFile.getAbsolutePath();

            // Also save to web-accessible directory for download links
            String webFileName = "workflow-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0, 8) + ".xlsx";
            File webDir = new File(System.getProperty("java.io.tmpdir"), "hr-workflow-files");
            if (!webDir.exists()) {
                webDir.mkdirs();
            }
            File webFile = new File(webDir, webFileName);
            try (FileOutputStream webOutputStream = new FileOutputStream(webFile)) {
                // Re-write the workbook to the web file
                Workbook webWorkbook = new XSSFWorkbook();
                Sheet webSheet = webWorkbook.createSheet(sheetName);
                copySheetData(sheet, webSheet); // Copy the data to new workbook
                webWorkbook.write(webOutputStream);
                webWorkbook.close();
            }

            String webFileUrl = "/api/v1/files/excel/" + webFileName;

            // Encode file content to base64
            byte[] fileBytes = Files.readAllBytes(tempFile.toPath());
            String fileContent = Base64.getEncoder().encodeToString(fileBytes);

            log.info("✅ Excel: File created - {} ({} bytes)", filePath, fileSize);

            // Build response
            ExcelResponseDTO response = new ExcelResponseDTO();
            response.setRowsProcessed(rowNum);
            response.setColumnsProcessed(columnsProcessed);
            response.setOutputFileUrl(filePath);
            response.setWebFileUrl(webFileUrl);
            response.setFileSize(fileSize);
            response.setSheetName(sheetName);
            response.setOperation(operation);
            response.setCreatedAt(LocalDateTime.now());
            response.setData(dataList);
            response.setFileContent(fileContent);

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("❌ Excel service failed", e);
            throw new RuntimeException("Excel processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extract JSON from text that may contain prefixes like "AI Analysis:"
     */
    private String extractJsonFromText(String text) {
        if (text == null) return text;
        
        // Look for JSON array starting with [
        int startIndex = text.indexOf('[');
        if (startIndex == -1) return text;
        
        // Find the end of the JSON array
        int braceCount = 0;
        int endIndex = -1;
        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') braceCount++;
            else if (c == ']') braceCount--;
            
            if (braceCount == 0) {
                endIndex = i + 1;
                break;
            }
        }
        
        if (endIndex != -1) {
            return text.substring(startIndex, endIndex);
        }
        
        return text;
    }

    /**
     * Copy sheet data from one sheet to another
     */
    private void copySheetData(Sheet sourceSheet, Sheet targetSheet) {
        for (int i = 0; i <= sourceSheet.getLastRowNum(); i++) {
            Row sourceRow = sourceSheet.getRow(i);
            if (sourceRow != null) {
                Row targetRow = targetSheet.createRow(i);
                for (int j = 0; j < sourceRow.getLastCellNum(); j++) {
                    Cell sourceCell = sourceRow.getCell(j);
                    if (sourceCell != null) {
                        Cell targetCell = targetRow.createCell(j);
                        // Copy cell value and style
                        switch (sourceCell.getCellType()) {
                            case STRING:
                                targetCell.setCellValue(sourceCell.getStringCellValue());
                                break;
                            case NUMERIC:
                                targetCell.setCellValue(sourceCell.getNumericCellValue());
                                break;
                            case BOOLEAN:
                                targetCell.setCellValue(sourceCell.getBooleanCellValue());
                                break;
                            default:
                                targetCell.setCellValue("");
                        }
                        // Note: Cell styles are not copied to avoid workbook compatibility issues
                    }
                }
            }
        }
    }

    /**
     * Create table from JSON data
     */
    private int createJsonTable(Workbook workbook, Sheet sheet, JsonNode dataNode, int rowNum) {
        Row headerRow = sheet.createRow(rowNum++);

        // Get field names from JSON
        List<String> fieldNames = new ArrayList<>();
        dataNode.fieldNames().forEachRemaining(fieldNames::add);

        // Write headers
        for (int i = 0; i < fieldNames.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(fieldNames.get(i));

            // Style header
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            cell.setCellStyle(headerStyle);
        }

        // Write data row
        Row dataRow = sheet.createRow(rowNum++);
        for (int i = 0; i < fieldNames.size(); i++) {
            Cell cell = dataRow.createCell(i);
            cell.setCellValue(dataNode.get(fieldNames.get(i)).asText());
        }

        // Auto-size columns
        for (int i = 0; i < fieldNames.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        return rowNum;
    }

    /**
     * Create table from JSON array of candidates
     */
    private int createCandidateArrayTable(Workbook workbook, Sheet sheet, JsonNode arrayNode, int rowNum) {
        if (!arrayNode.isArray() || arrayNode.size() == 0) {
            return rowNum;
        }

        // Create header styles
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        // Create header row
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Name", "Email", "Experience", "Skills", "Score"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Create data style
        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);

        // Score cell style (with color coding)
        CellStyle highScoreStyle = workbook.createCellStyle();
        highScoreStyle.cloneStyleFrom(dataStyle);
        highScoreStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        highScoreStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle mediumScoreStyle = workbook.createCellStyle();
        mediumScoreStyle.cloneStyleFrom(dataStyle);
        mediumScoreStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        mediumScoreStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle lowScoreStyle = workbook.createCellStyle();
        lowScoreStyle.cloneStyleFrom(dataStyle);
        lowScoreStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        lowScoreStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Add candidate data
        for (JsonNode candidateNode : arrayNode) {
            Row row = sheet.createRow(rowNum++);

            // Name
            Cell nameCell = row.createCell(0);
            nameCell.setCellValue(candidateNode.get("name").asText("N/A"));
            nameCell.setCellStyle(dataStyle);

            // Email
            Cell emailCell = row.createCell(1);
            emailCell.setCellValue(candidateNode.get("email").asText("N/A"));
            emailCell.setCellStyle(dataStyle);

            // Experience
            Cell expCell = row.createCell(2);
            expCell.setCellValue(candidateNode.get("experience").asText("N/A"));
            expCell.setCellStyle(dataStyle);

            // Skills
            Cell skillsCell = row.createCell(3);
            skillsCell.setCellValue(candidateNode.get("skills").asText("N/A"));
            skillsCell.setCellStyle(dataStyle);

            // Score (with color coding)
            Cell scoreCell = row.createCell(4);
            double score = candidateNode.get("score").asDouble(0.0);
            scoreCell.setCellValue(score);

            // Color code based on score
            if (score >= 8.0) {
                scoreCell.setCellStyle(highScoreStyle);
            } else if (score >= 6.0) {
                scoreCell.setCellStyle(mediumScoreStyle);
            } else {
                scoreCell.setCellStyle(lowScoreStyle);
            }
        }

        // Auto-size all columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            // Add extra width for readability
            sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
        }

        return rowNum;
    }

    /**
     * Create candidate ranking table
     */
    private int createCandidateTable(Workbook workbook, Sheet sheet, List<CandidateData> candidates, int rowNum) {
        // Create header styles
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        // Create header row
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Rank", "Name", "Email", "Experience (Years)", "Key Skills", "Score"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Create data style
        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);

        // Score cell style (with color coding)
        CellStyle highScoreStyle = workbook.createCellStyle();
        highScoreStyle.cloneStyleFrom(dataStyle);
        highScoreStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        highScoreStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle mediumScoreStyle = workbook.createCellStyle();
        mediumScoreStyle.cloneStyleFrom(dataStyle);
        mediumScoreStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        mediumScoreStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle lowScoreStyle = workbook.createCellStyle();
        lowScoreStyle.cloneStyleFrom(dataStyle);
        lowScoreStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        lowScoreStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Add candidate data
        int rank = 1;
        for (CandidateData candidate : candidates) {
            Row row = sheet.createRow(rowNum++);

            // Rank
            Cell rankCell = row.createCell(0);
            rankCell.setCellValue(rank++);
            rankCell.setCellStyle(dataStyle);

            // Name
            Cell nameCell = row.createCell(1);
            nameCell.setCellValue(candidate.name);
            nameCell.setCellStyle(dataStyle);

            // Email
            Cell emailCell = row.createCell(2);
            emailCell.setCellValue(candidate.email);
            emailCell.setCellStyle(dataStyle);

            // Experience
            Cell expCell = row.createCell(3);
            expCell.setCellValue(candidate.experience);
            expCell.setCellStyle(dataStyle);

            // Skills
            Cell skillsCell = row.createCell(4);
            skillsCell.setCellValue(candidate.skills);
            skillsCell.setCellStyle(dataStyle);

            // Score (with color coding)
            Cell scoreCell = row.createCell(5);
            scoreCell.setCellValue(candidate.score);

            // Color code based on score
            if (candidate.score >= 8.0) {
                scoreCell.setCellStyle(highScoreStyle);
            } else if (candidate.score >= 6.0) {
                scoreCell.setCellStyle(mediumScoreStyle);
            } else {
                scoreCell.setCellStyle(lowScoreStyle);
            }
        }

        // Auto-size all columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            // Add extra width for readability
            sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
        }

        return rowNum;
    }

    /**
     * Fallback: Create plain text sheet
     */
    private int createPlainTextSheet(Workbook workbook, Sheet sheet, String textData, int rowNum) {
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("AI Analysis Result");

        // Style title
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        titleCell.setCellStyle(titleStyle);

        // Add the text data
        Row contentRow = sheet.createRow(rowNum++);
        Cell contentCell = contentRow.createCell(0);
        contentCell.setCellValue(textData);

        // Auto-size column
        sheet.autoSizeColumn(0);
        sheet.setColumnWidth(0, 15000); // Set wider for text content

        return rowNum;
    }

    /**
     * Parse candidate information from GPT text output
     */
    private List<CandidateData> parseCandidatesFromText(String text) {
        List<CandidateData> candidates = new ArrayList<>();

        try {
            // Pattern to match candidate blocks
            // Matches: **Name** or 1. **Name** etc.
            Pattern candidatePattern = Pattern.compile(
                    "(?:\\d+\\.\\s*)?\\*\\*([^*]+)\\*\\*([^*]+?)(?=(?:\\d+\\.\\s*)?\\*\\*|$)",
                    Pattern.DOTALL
            );

            Matcher matcher = candidatePattern.matcher(text);

            while (matcher.find()) {
                String name = matcher.group(1).trim();
                String details = matcher.group(2).trim();

                CandidateData candidate = new CandidateData();
                candidate.name = name;

                // Extract email
                Pattern emailPattern = Pattern.compile("Email:\\s*([\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,})");
                Matcher emailMatcher = emailPattern.matcher(details);
                if (emailMatcher.find()) {
                    candidate.email = emailMatcher.group(1);
                }

                // Extract years of experience
                Pattern expPattern = Pattern.compile("(?:Years of Experience|Experience):\\s*(\\d+)");
                Matcher expMatcher = expPattern.matcher(details);
                if (expMatcher.find()) {
                    candidate.experience = expMatcher.group(1);
                }

                // Extract skills
                Pattern skillsPattern = Pattern.compile("(?:Key Skills|Skills):\\s*([^\n]+)");
                Matcher skillsMatcher = skillsPattern.matcher(details);
                if (skillsMatcher.find()) {
                    candidate.skills = skillsMatcher.group(1).trim();
                }

                // Extract score
                Pattern scorePattern = Pattern.compile("Score:\\s*([\\d.]+)");
                Matcher scoreMatcher = scorePattern.matcher(details);
                if (scoreMatcher.find()) {
                    try {
                        candidate.score = Double.parseDouble(scoreMatcher.group(1));
                    } catch (NumberFormatException e) {
                        candidate.score = 0.0;
                    }
                }

                // Only add if we found at least name and score
                if (candidate.name != null && !candidate.name.isEmpty() && candidate.score > 0) {
                    candidates.add(candidate);
                }
            }

        } catch (Exception e) {
            log.error("Error parsing candidates from text", e);
        }

        return candidates;
    }

    /**
     * Simple data class to hold candidate information
     */
    private static class CandidateData {
        String name = "";
        String email = "N/A";
        String experience = "N/A";
        String skills = "N/A";
        double score = 0.0;
    }
}