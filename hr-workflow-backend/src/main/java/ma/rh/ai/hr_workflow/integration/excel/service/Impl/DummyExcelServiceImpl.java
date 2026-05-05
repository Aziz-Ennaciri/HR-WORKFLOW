package ma.rh.ai.hr_workflow.integration.excel.service.Impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.integration.excel.DTOs.ExcelConfigDTO;
import ma.rh.ai.hr_workflow.integration.excel.DTOs.ExcelRequestDTO;
import ma.rh.ai.hr_workflow.integration.excel.DTOs.ExcelResponseDTO;
import ma.rh.ai.hr_workflow.integration.excel.service.ExcelService;

@Service
@RequiredArgsConstructor
public class DummyExcelServiceImpl implements ExcelService {

    private final ObjectMapper objectMapper;

    @Override
    public String processData(String configJson, String inputData) throws Exception {
        try {
            ExcelConfigDTO config = objectMapper.readValue(configJson, ExcelConfigDTO.class);
            ExcelRequestDTO request = objectMapper.readValue(inputData, ExcelRequestDTO.class);

            Thread.sleep(150);

            // Create dummy data
            List<Object> dummyData = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", i + 1);
                row.put("name", "Employee " + (i + 1));
                row.put("department", "Department " + (i + 1));
                dummyData.add(row);
            }

            ExcelResponseDTO response = new ExcelResponseDTO();
            response.setRowsProcessed(150);
            response.setColumnsProcessed(8);
            response.setOutputFileUrl("https://example.com/output.xlsx");
            response.setFileSize(2048L);
            response.setSheetName("Data");
            response.setOperation("WRITE");
            response.setCreatedAt(LocalDateTime.now());
            response.setData(dummyData);

            return objectMapper.writeValueAsString(response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Dummy Excel service interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Dummy Excel service failed: " + e.getMessage(), e);
        }
    }
}