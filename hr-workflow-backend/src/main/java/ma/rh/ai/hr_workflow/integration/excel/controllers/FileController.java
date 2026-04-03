package ma.rh.ai.hr_workflow.integration.excel.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/files")
@Slf4j
public class FileController {

    @GetMapping("/excel/{fileName}")
    public ResponseEntity<InputStreamResource> downloadExcel(@PathVariable String fileName) {
        try {
            File webDir = new File(System.getProperty("java.io.tmpdir"), "hr-workflow-files");
            File file = new File(webDir, fileName);

            if (!file.exists()) {
                log.warn("Excel file not found: {}", file.getAbsolutePath());
                return ResponseEntity.notFound().build();
            }

            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
            headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            headers.add(HttpHeaders.PRAGMA, "no-cache");
            headers.add(HttpHeaders.EXPIRES, "0");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(file.length())
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);

        } catch (IOException e) {
            log.error("Error downloading Excel file: {}", fileName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}