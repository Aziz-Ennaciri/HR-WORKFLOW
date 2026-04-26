package ma.rh.ai.hr_workflow.integration.excel.service.Impl;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                if (dataNode.has("analysis")) {
                    String analysisText = dataNode.get("analysis").asText();
                    log.info("📊 Excel: Detected GPT analysis field, dispatching adaptive formatter");
                    rowNum = createAdaptiveTextSheet(workbook, sheet, analysisText, rowNum);
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
            try (FileOutputStream webOutputStream = new FileOutputStream(webFile)) {
                Workbook webWorkbook = new XSSFWorkbook();
                Sheet webSheet = webWorkbook.createSheet(sheetName);
                copySheetData(sheet, webSheet);
                webWorkbook.write(webOutputStream);
                webWorkbook.close();
            }

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

    // ─── Sheet copy (for web-accessible duplicate) ────────────────────────────

    private void copySheetData(Sheet sourceSheet, Sheet targetSheet) {
        for (int i = 0; i <= sourceSheet.getLastRowNum(); i++) {
            Row sourceRow = sourceSheet.getRow(i);
            if (sourceRow == null) continue;
            Row targetRow = targetSheet.createRow(i);
            for (int j = 0; j < sourceRow.getLastCellNum(); j++) {
                Cell sourceCell = sourceRow.getCell(j);
                if (sourceCell == null) continue;
                Cell targetCell = targetRow.createCell(j);
                switch (sourceCell.getCellType()) {
                    case STRING  -> targetCell.setCellValue(sourceCell.getStringCellValue());
                    case NUMERIC -> targetCell.setCellValue(sourceCell.getNumericCellValue());
                    case BOOLEAN -> targetCell.setCellValue(sourceCell.getBooleanCellValue());
                    default      -> targetCell.setCellValue("");
                }
            }
        }
    }
}
