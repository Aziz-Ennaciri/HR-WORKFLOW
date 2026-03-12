package ma.rh.ai.hr_workflow.integration.excel.service.Impl;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

            // Parse input data as JSON
            JsonNode dataNode = objectMapper.readTree(inputData);

            // Create workbook
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet(sheetName);

            // Write headers and data
            int rowNum = 0;
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

            // Save to temp file
            File tempFile = File.createTempFile("workflow-", ".xlsx");
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                workbook.write(outputStream);
            }
            workbook.close();

            long fileSize = tempFile.length();
            String filePath = tempFile.getAbsolutePath();

            log.info("✅ Excel: File created - {} ({} bytes)", filePath, fileSize);

            // Build response
            ExcelResponseDTO response = new ExcelResponseDTO();
            response.setRowsProcessed(1);
            response.setColumnsProcessed(fieldNames.size());
            response.setOutputFileUrl(filePath);
            response.setFileSize(fileSize);
            response.setSheetName(sheetName);
            response.setOperation(operation);
            response.setCreatedAt(LocalDateTime.now());

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("❌ Excel service failed", e);
            throw new RuntimeException("Excel processing failed: " + e.getMessage(), e);
        }
    }
}