package ma.rh.ai.hr_workflow.integration.excel.service.Impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ma.rh.ai.hr_workflow.exceptions.service.ExcelServiceException;
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
    public String processData(String configJson, String inputData){
        try {

            objectMapper.readValue(configJson, ExcelConfigDTO.class);
            objectMapper.readValue(inputData, ExcelRequestDTO.class);

            Thread.sleep(150);

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
            throw new ExcelServiceException("Dummy Excel service interrupted",e);

        } catch (Exception e) {
            throw new ExcelServiceException("Dummy Excel service failed",e);
        }
    }

    @Override
    public String readData(String configJson) {
        try {
            List<Map<String, String>> dummyRows = new ArrayList<>();

            Map<String, String> row1 = new LinkedHashMap<>();
            row1.put("Name", "Fatima Alaoui");
            row1.put("Email", "fatima@example.com");
            row1.put("Department", "Engineering");
            dummyRows.add(row1);

            Map<String, String> row2 = new LinkedHashMap<>();
            row2.put("Name", "Ahmed Benali");
            row2.put("Email", "ahmed@example.com");
            row2.put("Department", "HR");
            dummyRows.add(row2);

            return objectMapper.writeValueAsString(dummyRows);

        } catch (Exception e) {
            throw new ExcelServiceException("Dummy Excel read failed", e);
        }
    }
}