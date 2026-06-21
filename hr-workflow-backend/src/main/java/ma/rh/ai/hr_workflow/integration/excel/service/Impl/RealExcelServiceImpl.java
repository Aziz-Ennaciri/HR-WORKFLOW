package ma.rh.ai.hr_workflow.integration.excel.service.Impl;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${drive.storage.path:/tmp/hr-workflow-files}")
    private String storagePath;

    @Override
    public String processData(String configJson, String inputData) throws Exception {
        try {
            log.info("📊 Excel: Processing data...");

            ExcelConfigDTO config = objectMapper.readValue(configJson, ExcelConfigDTO.class);

            String sheetName = config.getSheetName() != null ? config.getSheetName() : "Data";
            String operation = config.getOperation() != null ? config.getOperation() : "WRITE";

            JsonNode dataNode = null;
            String textData = null;
            try {
                dataNode = objectMapper.readTree(inputData);
            } catch (Exception e) {
                textData = inputData;
            }

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet(sheetName);

            int rowNum = 0;
            int columnsProcessed = 1;
            List<Object> dataList = null;

            if (dataNode != null) {
                if (dataNode.has("jsonData") && dataNode.get("jsonData").isArray()
                        && dataNode.get("jsonData").size() > 0) {
                    // Structured JSON array produced by GPT when outputFormat=json
                    JsonNode jsonDataNode = dataNode.get("jsonData");
                    log.info("📊 Excel: Detected jsonData array ({} items) — professional table formatter",
                            jsonDataNode.size());
                    rowNum = createJsonArraySheet(workbook, sheet, jsonDataNode, rowNum);
                    columnsProcessed = jsonDataNode.get(0).size();
                    dataList = objectMapper.convertValue(jsonDataNode, List.class);
                } else if (dataNode.has("analysis")) {
                    String analysisText = dataNode.get("analysis").asText();
                    // Try to parse as a JSON array first — AI now always returns JSON.
                    // Falls back to text formatting if the response isn't valid JSON.
                    JsonNode analysisJson = tryParseJsonArray(analysisText);
                    if (analysisJson != null) {
                        log.info("📊 Excel: analysis field contains JSON array ({} items) — professional table",
                                analysisJson.size());
                        rowNum = createJsonArraySheet(workbook, sheet, analysisJson, rowNum);
                        columnsProcessed = analysisJson.size() > 0 ? analysisJson.get(0).size() : 1;
                        dataList = objectMapper.convertValue(analysisJson, List.class);
                    } else {
                        log.info("📊 Excel: analysis is plain text — dispatching adaptive formatter");
                        rowNum = createAdaptiveTextSheet(workbook, sheet, analysisText, rowNum);
                    }
                } else {
                    rowNum = createJsonTable(workbook, sheet, dataNode, rowNum);
                    columnsProcessed = dataNode.size();
                    dataList = objectMapper.convertValue(dataNode, List.class);
                }
            } else if (textData != null) {
                log.info("📊 Excel: Raw text input, dispatching adaptive formatter");
                rowNum = createAdaptiveTextSheet(workbook, sheet, textData, rowNum);
            }

            File tempFile = File.createTempFile("workflow-", ".xlsx");
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                workbook.write(outputStream);
            }
            workbook.close();

            long fileSize = tempFile.length();
            String filePath = tempFile.getAbsolutePath();

            String webFileName = "workflow-" + System.currentTimeMillis() + "-"
                    + java.util.UUID.randomUUID().toString().substring(0, 8) + ".xlsx";
            File webDir = new File(System.getProperty("java.io.tmpdir"), "hr-workflow-files");
            if (!webDir.exists()) {
                webDir.mkdirs();
            }
            File webFile = new File(webDir, webFileName);
            // Copy the already-formatted file byte-for-byte so the email download
            // link serves the same styled workbook as the app download.
            Files.copy(tempFile.toPath(), webFile.toPath());

            String webFileUrl = "/api/v1/files/excel/" + webFileName;

            byte[] fileBytes = Files.readAllBytes(tempFile.toPath());
            String fileContent = Base64.getEncoder().encodeToString(fileBytes);

            log.info("✅ Excel: File created - {} ({} bytes)", filePath, fileSize);

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

    // ─── READ operation ───────────────────────────────────────────────────────

    @Override
    public String readData(String configJson) throws Exception {
        try {
            ExcelConfigDTO config = objectMapper.readValue(configJson, ExcelConfigDTO.class);

            String folderId = config.getFolderId() != null ? config.getFolderId() : "";
            String fileName = config.getFileName();
            if (fileName == null || fileName.isBlank()) {
                throw new RuntimeException("fileName is required for READ operation");
            }

            Path filePath = folderId.isBlank()
                    ? Paths.get(storagePath, fileName)
                    : Paths.get(storagePath, folderId, fileName);

            log.info("📖 Excel READ: reading file {}", filePath);

            if (!Files.exists(filePath)) {
                throw new RuntimeException("File not found: " + filePath);
            }

            String lowerName = fileName.toLowerCase();
            String result;
            if (lowerName.endsWith(".csv")) {
                result = readCsvFile(filePath);
            } else {
                result = readXlsxFile(filePath);
            }

            log.info("✅ Excel READ: parsed {} — returning JSON array", fileName);
            return result;

        } catch (Exception e) {
            log.error("❌ Excel READ failed", e);
            throw new RuntimeException("Excel read failed: " + e.getMessage(), e);
        }
    }

    private String readXlsxFile(Path filePath) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(filePath.toFile())) {
            Sheet sheet = workbook.getSheetAt(0);

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return "[]";
            }

            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(getCellStringValue(cell));
            }

            List<Map<String, String>> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c);
                    rowMap.put(headers.get(c), cell != null ? getCellStringValue(cell) : "");
                }
                rows.add(rowMap);
            }

            return objectMapper.writeValueAsString(rows);
        }
    }

    private String readCsvFile(Path filePath) throws Exception {
        List<String> lines = Files.readAllLines(filePath);
        if (lines.isEmpty()) {
            return "[]";
        }

        String[] headers = lines.get(0).split(",", -1);
        for (int i = 0; i < headers.length; i++) {
            headers[i] = headers[i].trim();
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            String[] values = line.split(",", -1);
            Map<String, String> rowMap = new LinkedHashMap<>();
            for (int j = 0; j < headers.length; j++) {
                rowMap.put(headers[j], j < values.length ? values[j].trim() : "");
            }
            rows.add(rowMap);
        }

        return objectMapper.writeValueAsString(rows);
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    return String.valueOf((long) val);
                }
                return String.valueOf(val);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    // ─── Adaptive dispatcher ──────────────────────────────────────────────────

    private int createAdaptiveTextSheet(Workbook workbook, Sheet sheet, String text, int rowNum) {
        if (isMarkdownTable(text)) {
            log.info("📊 Excel: Markdown table format detected");
            return createMarkdownTableSheet(workbook, sheet, text, rowNum);
        }
        if (isBlockSections(text)) {
            log.info("📊 Excel: Block/section format detected");
            return createBlockSectionsSheet(workbook, sheet, text, rowNum);
        }
        log.info("📊 Excel: Plain text format — writing line by line");
        return createPlainTextSheet(workbook, sheet, text, rowNum);
    }

    private boolean isMarkdownTable(String text) {
        long tableLines = Arrays.stream(text.split("\n"))
                .filter(line -> line.contains("|"))
                .filter(line -> line.split("\\|").length >= 3)
                .count();
        return tableLines >= 2;
    }

    private boolean isBlockSections(String text) {
        String[] lines = text.split("\n");
        boolean hasBlockStart = false;
        boolean hasBullets = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("^\\d+[.)\\s].+") || trimmed.matches("^#{1,3}\\s+.+")) {
                hasBlockStart = true;
            }
            if (trimmed.matches("^[\\*\\-\\+]\\s+.+")) {
                hasBullets = true;
            }
        }
        return hasBlockStart && hasBullets;
    }

    // ─── Format 1: Markdown table ─────────────────────────────────────────────

    private int createMarkdownTableSheet(Workbook workbook, Sheet sheet, String text, int rowNum) {
        CellStyle headerStyle = buildHeaderStyle(workbook);
        CellStyle dataStyle = buildDataStyle(workbook);

        boolean headerWritten = false;
        int maxCols = 0;

        for (String line : text.split("\n")) {
            if (!line.contains("|")) continue;

            // Skip pure separator lines like |---|---|
            if (line.replaceAll("[|\\-:\\s]", "").isEmpty()) continue;

            List<String> cells = new ArrayList<>();
            for (String part : line.split("\\|")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) cells.add(trimmed);
            }
            if (cells.isEmpty()) continue;

            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(20);
            CellStyle style = headerWritten ? dataStyle : headerStyle;

            for (int i = 0; i < cells.size(); i++) {
                Cell cell = row.createCell(i);
                cell.setCellValue(cells.get(i));
                cell.setCellStyle(style);
            }
            maxCols = Math.max(maxCols, cells.size());
            headerWritten = true;
        }

        for (int i = 0; i < maxCols; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 1000, 65280));
        }
        return rowNum;
    }

    // ─── Format 2: Numbered / bulleted blocks ─────────────────────────────────

    private int createBlockSectionsSheet(Workbook workbook, Sheet sheet, String text, int rowNum) {
        Pattern blockStart = Pattern.compile("^\\d+[.)\\s]\\s*(.+)|^#{1,3}\\s+(.+)");
        Pattern bullet = Pattern.compile("^[\\*\\-\\+]\\s+(.+)");

        List<Map<String, String>> blocks = new ArrayList<>();
        List<String> keyOrder = new ArrayList<>();

        Map<String, String> current = null;
        int blockNum = 0;

        for (String rawLine : text.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            Matcher bm = blockStart.matcher(line);
            if (bm.matches()) {
                if (current != null) blocks.add(current);
                String title = bm.group(1) != null ? bm.group(1).trim() : bm.group(2).trim();
                current = new LinkedHashMap<>();
                current.put("__num__", String.valueOf(++blockNum));
                current.put("__title__", title);
                continue;
            }

            if (current != null) {
                Matcher pm = bullet.matcher(line);
                if (pm.matches()) {
                    String content = pm.group(1).trim();
                    int colon = content.indexOf(':');
                    if (colon > 0) {
                        String key = content.substring(0, colon).trim();
                        String value = content.substring(colon + 1).trim();
                        current.put(key, value);
                        if (!keyOrder.contains(key)) keyOrder.add(key);
                    } else {
                        String existing = current.getOrDefault("Notes", "");
                        current.put("Notes", existing.isEmpty() ? content : existing + "; " + content);
                        if (!keyOrder.contains("Notes")) keyOrder.add("Notes");
                    }
                }
            }
        }
        if (current != null) blocks.add(current);

        if (blocks.isEmpty()) {
            return createPlainTextSheet(workbook, sheet, text, rowNum);
        }

        CellStyle headerStyle = buildHeaderStyle(workbook);
        CellStyle dataStyle = buildDataStyle(workbook);

        // Header row
        List<String> headers = new ArrayList<>();
        headers.add("#");
        headers.add("Title");
        headers.addAll(keyOrder);

        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(20);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }

        // Data rows
        for (Map<String, String> block : blocks) {
            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(20);

            Cell numCell = row.createCell(0);
            numCell.setCellValue(block.getOrDefault("__num__", ""));
            numCell.setCellStyle(dataStyle);

            Cell titleCell = row.createCell(1);
            titleCell.setCellValue(block.getOrDefault("__title__", ""));
            titleCell.setCellStyle(dataStyle);

            for (int i = 0; i < keyOrder.size(); i++) {
                Cell cell = row.createCell(i + 2);
                cell.setCellValue(block.getOrDefault(keyOrder.get(i), ""));
                cell.setCellStyle(dataStyle);
            }
        }

        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 1000, 65280));
        }
        return rowNum;
    }

    // ─── Format 3: Plain text fallback ───────────────────────────────────────

    private int createPlainTextSheet(Workbook workbook, Sheet sheet, String text, int rowNum) {
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);

        CellStyle wrapStyle = workbook.createCellStyle();
        wrapStyle.setWrapText(true);

        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(20);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("AI Analysis Result");
        titleCell.setCellStyle(titleStyle);

        rowNum++; // blank separator

        for (String line : text.split("\n", -1)) {
            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(20);
            Cell cell = row.createCell(0);
            cell.setCellValue(line);
            if (!line.isEmpty()) cell.setCellStyle(wrapStyle);
        }

        sheet.setColumnWidth(0, Math.min(25000, 65280));
        return rowNum;
    }

    // ─── JSON parse helper ────────────────────────────────────────────────────

    private JsonNode tryParseJsonArray(String text) {
        if (text == null || text.isBlank()) return null;
        String cleaned = text.trim();
        // Strip markdown code fences (```json ... ``` or ``` ... ```)
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) return null;
        String candidate = cleaned.substring(start, end + 1).trim();
        try {
            JsonNode node = objectMapper.readTree(candidate);
            // Must be a non-empty array of objects to be treated as structured data
            if (node.isArray() && node.size() > 0 && node.get(0).isObject()) {
                return node;
            }
        } catch (Exception e) {
            // Not valid JSON
        }
        return null;
    }

    // ─── Format 4: Structured JSON array (from GPT or jsonData field) ─────────

    // Preferred column order — fields found in the AI response are sorted by this list first,
    // then any extra fields the AI added come after.
    private static final List<String> PREFERRED_COLS =
            Arrays.asList("rank", "name", "email", "experience", "skills", "score", "summary");

    private int createJsonArraySheet(Workbook workbook, Sheet sheet, JsonNode arrayNode, int rowNum) {
        // Collect all field names from the first object
        List<String> rawHeaders = new ArrayList<>();
        arrayNode.get(0).fieldNames().forEachRemaining(rawHeaders::add);

        // Sort by preference: preferred columns first, then any extras the AI added
        List<String> headers = new ArrayList<>();
        for (String preferred : PREFERRED_COLS) {
            if (rawHeaders.contains(preferred)) headers.add(preferred);
        }
        for (String key : rawHeaders) {
            if (!headers.contains(key)) headers.add(key);
        }

        CellStyle headerStyle  = buildProfessionalHeaderStyle(workbook);
        CellStyle evenRowStyle = buildEvenRowStyle(workbook);
        CellStyle oddRowStyle  = buildOddRowStyle(workbook);

        // Header row
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(prettifyHeader(headers.get(i)));
            cell.setCellStyle(headerStyle);
        }

        // Data rows — alternating colors
        for (int r = 0; r < arrayNode.size(); r++) {
            JsonNode obj = arrayNode.get(r);
            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(18);
            CellStyle rowStyle = (r % 2 == 0) ? evenRowStyle : oddRowStyle;

            for (int c = 0; c < headers.size(); c++) {
                Cell cell = row.createCell(c);
                JsonNode val = obj.get(headers.get(c));
                cell.setCellValue(val != null ? val.asText() : "");
                cell.setCellStyle(rowStyle);
            }
        }

        // Auto-size with min/max caps for readability
        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
            int w = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.min(Math.max(w + 512, 3000), 25600));
        }
        return rowNum;
    }

    private String prettifyHeader(String key) {
        String spaced = key
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace("_", " ");
        return Arrays.stream(spaced.trim().split("\\s+"))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    // ─── Professional style builders (XSSF-specific for custom colours) ───────

    private CellStyle buildProfessionalHeaderStyle(Workbook workbook) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        XSSFFont font = (XSSFFont) workbook.createFont();
        font.setFontName("Calibri");
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null)); // white
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 26, (byte) 54, (byte) 93}, null)); // #1a365d
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle buildEvenRowStyle(Workbook workbook) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        XSSFFont font = (XSSFFont) workbook.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle buildOddRowStyle(Workbook workbook) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        XSSFFont font = (XSSFFont) workbook.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 248, (byte) 249, (byte) 250}, null)); // #f8f9fa
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    // ─── Style helpers ────────────────────────────────────────────────────────

    private CellStyle buildHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle buildDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    // ─── JSON table (non-GPT input) ───────────────────────────────────────────

    private int createJsonTable(Workbook workbook, Sheet sheet, JsonNode dataNode, int rowNum) {
        List<String> fieldNames = new ArrayList<>();
        dataNode.fieldNames().forEachRemaining(fieldNames::add);

        CellStyle headerStyle = buildHeaderStyle(workbook);

        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(20);
        for (int i = 0; i < fieldNames.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(fieldNames.get(i));
            cell.setCellStyle(headerStyle);
        }

        Row dataRow = sheet.createRow(rowNum++);
        dataRow.setHeightInPoints(20);
        for (int i = 0; i < fieldNames.size(); i++) {
            Cell cell = dataRow.createCell(i);
            cell.setCellValue(dataNode.get(fieldNames.get(i)).asText());
        }

        for (int i = 0; i < fieldNames.size(); i++) {
            sheet.autoSizeColumn(i);
        }
        return rowNum;
    }

}
