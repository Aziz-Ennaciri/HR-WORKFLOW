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
public class DummyExcelServiceImpl implements ExcelService{
    private final ObjectMapper objectMapper;

    @Override
    public String processExcel(String configJson, String inputData) throws Exception {
        try {
            ExcelConfigDTO config = objectMapper.readValue(configJson, ExcelConfigDTO.class);
            
            ExcelRequestDTO request = objectMapper.readValue(inputData, ExcelRequestDTO.class);

            Thread.sleep(300);

            ExcelResponseDTO response = new ExcelResponseDTO();
            response.setRowsProcessed(150);
            response.setColumnsProcessed(8);
            
            List<Map<String, Object>> mockData = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", i + 1);
                row.put("name", "Row " + (i + 1));
                row.put("value", Math.random() * 1000);
                mockData.add(row);
            }
            response.setData(mockData);
            response.setOutputFileUrl("https://example.com/output.xlsx");
            response.setProcessedAt(LocalDateTime.now());

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            throw new RuntimeException("ExcelService error", e);
        }
    }
}
